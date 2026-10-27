package bio.cosy.feddb.local.api.importer.transformer;

import bio.cosy.feddb.core.execution.run.dto.AppMessageWrapperDTO;
import bio.cosy.feddb.core.execution.run.dto.StartRunDTO;
import bio.cosy.feddb.core.execution.run.types.AppMessageTypeEnum;
import bio.cosy.feddb.core.execution.run.types.AppRunTypeEnum;
import bio.cosy.feddb.core.execution.run.types.RunStatusTypes;
import bio.cosy.feddb.local.api.importer.connector.ConnectorDTO;
import bio.cosy.feddb.local.api.importer.connector.ConnectorHelper;
import bio.cosy.feddb.local.api.importer.files.table.TableData;
import bio.cosy.feddb.local.api.importer.run.AppTransformerSessionFailure;
import bio.cosy.feddb.local.api.importer.run.ConnectorAppRunBO;
import bio.cosy.feddb.local.api.importer.run.ConnectorRunDTO;
import bio.cosy.feddb.local.execution.run.ExecutionRunBO;
import bio.cosy.feddb.local.execution.run.ExecutionRunDTO;
import io.quarkus.logging.Log;
import io.quarkus.websockets.next.WebSocketConnection;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import static bio.cosy.feddb.local.api.importer.transformer.ConnectorTransformerBO.APP_OUTPUT_TIMEOUT;

/**
 * Keeps one running app per app-based transformer for the length of an import and feeds it the import's batches
 * one after another, each as another run on the connected app. Starting an app per batch, an image pull and a
 * container start each, made app-based transformation unusable on any real import. The ETL groups by patient
 * before transforming, so an app that reasons about one patient at a time still sees whole patients.
 */
@ApplicationScoped
public class AppTransformerSessionBO {

    // The image is pulled and the container started before this waits, a healthy app connects within seconds.
    private static final Duration CONNECT_TIMEOUT = Duration.ofMinutes(2);
    // The app reconnects every five seconds when its socket drops, so a batch waits for several attempts.
    private static final Duration RECONNECT_TIMEOUT = Duration.ofSeconds(60);
    // Name of the app input a transformer reads its rows from; fixed by the transformer contract.
    private static final String INPUT_VARIABLE = "input";

    @Inject
    ConnectorAppRunBO appRunBO;

    @Inject
    ExecutionRunBO runBO;

    // Import or preview -> transformer -> attempt of the app serving that transformer.
    private final Map<Long, Map<Long, Long>> sessions = new ConcurrentHashMap<>();

    // Batch ids count down from zero, so they never collide with an attempt id.
    private final AtomicLong batchIds = new AtomicLong();
    private final AtomicLong previewIds = new AtomicLong();

    public Long openPreview(ConnectorTransformerDTO transformer, String keycloakId, Consumer<ExecutionRunDTO> onRunCreated) {
        Long previewId = previewIds.decrementAndGet();
        Map<Long, Long> forPreview = new ConcurrentHashMap<>();
        sessions.put(previewId, forPreview);
        try {
            forPreview.put(sessionKeyOf(transformer, previewId), start(transformer, null, keycloakId, onRunCreated));
        } catch (RuntimeException e) {
            close(previewId);
            throw e;
        }
        return previewId;
    }

    /**
     * Starts the app of every app-based transformer of the connector and waits until each connected.
     */
    public void open(ConnectorDTO connector, ConnectorRunDTO run) {
        List<ConnectorTransformerDTO> transformers = connector.getAppTransformers();
        if (transformers.isEmpty()) {
            return;
        }
        Map<Long, Long> forRun = new ConcurrentHashMap<>();
        sessions.put(run.getId(), forRun);
        Log.infof("Starting %d app-based transformer(s) for import %d", transformers.size(), run.getId());
        try {
            for (ConnectorTransformerDTO transformer : transformers) {
                forRun.put(sessionKeyOf(transformer, run.getId()), start(transformer, run.getId(), run.getKeycloakId(), created -> {
                }));
            }
        } catch (RuntimeException e) {
            close(run.getId());
            throw e;
        }
    }

    public List<Map<String, Object>> transform(Long sessionId, ConnectorTransformerDTO transformer, List<Map<String, Object>> rows) {
        Long attemptId = Optional.ofNullable(sessions.get(sessionId))
                .map(forSession -> forSession.get(sessionKeyOf(transformer, sessionId)))
                .orElseThrow(() -> new AppTransformerSessionFailure("No running app transformer for '"
                        + transformer.getAppImage() + "' in import " + sessionId));
        WebSocketConnection connection = connectionOf(attemptId, transformer);
        Long batchId = batchIds.decrementAndGet();
        CompletableFuture<TableData> result = appRunBO.expectBatch(batchId);
        StartRunDTO start = new StartRunDTO();
        start.setId(batchId);
        start.setStatus(RunStatusTypes.PENDING);
        start.setHyperParams(transformer.getHyperparams());
        LinkedHashMap<String, Object> inputData = new LinkedHashMap<>();
        inputData.put(INPUT_VARIABLE, rows);
        start.setInputData(inputData);
        start.setInputFilePaths(new LinkedHashMap<>());
        try {
            // The ETL hands its batches over one after another, so it waits here for the app's upload.
            TableData answered = connection.sendText(new AppMessageWrapperDTO<>(AppMessageTypeEnum.START_PREDICTION, start, AppRunTypeEnum.MODEL_RUN))
                    .replaceWith(Uni.createFrom().completionStage(result))
                    .ifNoItem().after(APP_OUTPUT_TIMEOUT).fail()
                    .await().indefinitely();
            try (TableData output = answered) {
                return ConnectorHelper.mergeRows(rows, output.getRows(), transformer);
            }
        } catch (RuntimeException e) {
            throw new AppTransformerSessionFailure("App transformer '" + transformer.getAppImage() + "' failed on a batch: " + e.getMessage(), e);
        } finally {
            appRunBO.forgetBatch(batchId);
        }
    }

    /**
     * Stops every app the import or preview started, whether it succeeded or failed.
     */
    public void close(Long sessionId) {
        Map<Long, Long> forSession = sessions.remove(sessionId);
        if (forSession == null) {
            return;
        }
        forSession.values().forEach(attemptId -> {
            try {
                appRunBO.endSession(attemptId);
            } catch (RuntimeException e) {
                Log.errorf("Could not stop the app of attempt %d: %s", attemptId, e.getMessage());
            }
        });
    }

    private Long start(ConnectorTransformerDTO transformer, Long connectorRunId, String keycloakId, Consumer<ExecutionRunDTO> onRunCreated) {
        ExecutionRunDTO run = appRunBO.startTransactional(transformer, transformer.getHyperparams(), connectorRunId, null, keycloakId, true);
        onRunCreated.accept(run);
        // The import runs batch after batch, so it waits here; the app's connect ends the wait, not a poll.
        Long attemptId = appRunBO.connectedAttemptId(run.getId())
                .onFailure(AppTransformerSessionFailure.class).transform(e -> new AppTransformerSessionFailure("App transformer '"
                        + transformer.getAppImage() + "' " + e.getMessage() + " before it could take any work", e))
                .ifNoItem().after(CONNECT_TIMEOUT).recoverWithUni(() -> timedOut(transformer, run.getId(),
                        // Most often an image built against a wrapper without API-key auth: it starts, fails the handshake
                        // and stays silent, its logs travel over the socket it could not open.
                        "did not connect within " + CONNECT_TIMEOUT.toSeconds() + "s. The container started but never opened its"
                                + " websocket - check that the image is built against a pyfedappwrap that supports API-key authentication"))
                .await().indefinitely();
        Log.infof("App transformer '%s' ready as attempt %d", transformer.getAppImage(), attemptId);
        return attemptId;
    }

    // The app drops its socket now and then and connects again on its own.
    private WebSocketConnection connectionOf(Long attemptId, ConnectorTransformerDTO transformer) {
        Optional<WebSocketConnection> open = runBO.connectionOf(attemptId);
        if (open.isPresent()) {
            return open.get();
        }
        appRunBO.connectedAttemptId(appRunBO.sessionRunIdOf(attemptId))
                .ifNoItem().after(RECONNECT_TIMEOUT).recoverWithUni(() -> timedOut(transformer, null,
                        "did not reconnect within " + RECONNECT_TIMEOUT.toSeconds() + "s"))
                .await().indefinitely();
        return runBO.connectionOf(attemptId).orElseThrow(() -> new AppTransformerSessionFailure(
                "App transformer '" + transformer.getAppImage() + "' dropped its connection"));
    }

    // Fails the app that never connected, if it got an attempt.
    private Uni<Long> timedOut(ConnectorTransformerDTO transformer, Long runId, String failure) {
        AppTransformerSessionFailure timedOut = new AppTransformerSessionFailure("App transformer '" + transformer.getAppImage() + "' " + failure);
        return Uni.createFrom().<Long>item(() -> {
                    if (runId != null) {
                        runBO.findLatestAttemptIdTransactional(runId).ifPresent(attemptId -> appRunBO.failAttempt(attemptId, timedOut.getMessage()));
                    }
                    return null;
                })
                .runSubscriptionOn(Infrastructure.getDefaultWorkerPool())
                .onItem().failWith(() -> timedOut);
    }

    private Long sessionKeyOf(ConnectorTransformerDTO transformer, Long sessionId) {
        return transformer.getId() != null ? transformer.getId() : sessionId;
    }
}
