package bio.cosy.feddb.local.api.importer.transformer;

import bio.cosy.feddb.core.api.run.AppMessageTypeEnum;
import bio.cosy.feddb.core.api.run.AppMessageWrapperDTO;
import bio.cosy.feddb.core.api.run.AppRunTypeEnum;
import bio.cosy.feddb.core.api.run.RunStatusTypes;
import bio.cosy.feddb.core.api.run.StartRunDTO;
import bio.cosy.feddb.core.security.Scope;
import bio.cosy.feddb.core.security.ToolApiKeyService;
import bio.cosy.feddb.local.api.importer.connector.ConnectorDTO;
import bio.cosy.feddb.local.api.importer.files.table.TableData;
import bio.cosy.feddb.local.api.importer.run.ConnectorRunDTO;
import bio.cosy.feddb.local.api.importer.run.execution.ConnectorRunExecutionOutputAwaiter;
import bio.cosy.feddb.local.api.importer.run.execution.ConnectorRunExecutionRunBO;
import bio.cosy.feddb.local.api.importer.run.execution.ConnectorRunExecutionWebsocketService;
import bio.cosy.feddb.local.api.importer.run.step.ConnectorRunStepBO;
import bio.cosy.feddb.local.api.importer.run.step.ConnectorRunStepDTO;
import io.quarkus.logging.Log;
import io.quarkus.websockets.next.OpenConnections;
import io.quarkus.websockets.next.WebSocketConnection;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import static bio.cosy.feddb.local.api.importer.transformer.ConnectorTransformerBO.APP_OUTPUT_TIMEOUT;

/**
 * Keeps one running container per app-based transformer for the length of an import run and feeds
 * it the run's batches one after another.
 *
 * <p>The streaming ETL hands the transform stage a batch of patients at a time, and the previous
 * implementation started a fresh container for every one of those batches - an image pull and a
 * container start per batch, which is why app-based transformation was unusable on any real
 * import. The wrapper accepts a new run on an already-connected app once the previous one has
 * finished ({@code WorkerManager.start_worker} refuses only while a worker is live), so the
 * container is started once here, before the drain loop, and each batch is another run on it.</p>
 *
 * <p>Batches are patient-aligned because the ETL groups by patient before transforming, so an app
 * that can only reason about one patient at a time still sees whole patients.</p>
 */
@ApplicationScoped
public class AppTransformerSessionBO {

    /**
     * How long to wait for a started app to open its websocket before giving up on the run.
     *
     * <p>The image has already been pulled and the container started by the time this waits, so a
     * healthy app connects within seconds; the budget is generous only to absorb a slow interpreter
     * start. It is deliberately not the five-minute output timeout - an app that is never going to
     * connect should fail the run quickly rather than look like a hang.</p>
     */
    private static final Duration CONNECT_TIMEOUT = Duration.ofMinutes(2);
    private static final Duration CONNECT_POLL = Duration.ofMillis(250);
    private static final Duration CONNECT_LOG_INTERVAL = Duration.ofSeconds(15);

    /**
     * How long a batch waits for a dropped app to come back. The wrapper retries every five
     * seconds, so this allows for several attempts before the run gives up on it.
     */
    private static final Duration RECONNECT_TIMEOUT = Duration.ofSeconds(60);

    @Inject
    ConnectorRunExecutionRunBO executionRunBO;

    @Inject
    ConnectorRunStepBO stepBO;

    @Inject
    ToolApiKeyService toolApiKeyService;

    @Inject
    ConnectorRunExecutionOutputAwaiter outputAwaiter;

    @Inject
    OpenConnections connections;

    /** runId -> transformerId -> the container serving that transformer for this run. */
    private final Map<Long, Map<Long, TransformerSession>> sessions = new ConcurrentHashMap<>();

    /** Steps this BO drives, so the websocket endpoint does not also start them on connect. */
    private final Set<Long> sessionSteps = ConcurrentHashMap.newKeySet();

    /**
     * Batch ids count down from zero so they can never collide with a step id, which is a positive
     * database identity. The upload endpoint tells a batch result from a step result by this id.
     */
    private final AtomicLong batchIds = new AtomicLong();

    private final AtomicLong previewSessionIds = new AtomicLong();

    private record TransformerSession(Long stepId, ConnectorTransformerDTO transformer) {
    }

    /** True when this step is driven by a session, so nothing else should start a run on it. */
    public boolean isSessionStep(Long stepId) {
        return stepId != null && sessionSteps.contains(stepId);
    }

    public Long openPreview(ConnectorTransformerDTO transformer,
                            String keycloakId,
                            Consumer<ConnectorRunStepDTO> onStepCreated) {
        Long sessionId = previewSessionIds.decrementAndGet();
        Map<Long, TransformerSession> forSession = new ConcurrentHashMap<>();
        sessions.put(sessionId, forSession);
        Log.infof("Starting app transformer '%s' for a preview", transformer.getAppImage());
        try {
            forSession.put(keyOf(transformer, sessionId),
                    start(transformer, null, keycloakId, onStepCreated));
        } catch (RuntimeException e) {
            close(sessionId);
            throw e;
        }
        return sessionId;
    }

    private static Long keyOf(ConnectorTransformerDTO transformer, Long sessionId) {
        return transformer.getId() != null ? transformer.getId() : sessionId;
    }

    /**
     * Starts one container for every app-based transformer of the connector and waits until each
     * has connected back, so the drain loop can assume every app is ready to take batches.
     *
     * @throws AppTransformerSessionException if any of them cannot be started or never connects
     */
    public void open(ConnectorDTO connector, ConnectorRunDTO run) {
        List<ConnectorTransformerDTO> appTransformers = appTransformersOf(connector);
        if (appTransformers.isEmpty()) {
            return;
        }

        Map<Long, TransformerSession> forRun = new ConcurrentHashMap<>();
        sessions.put(run.getId(), forRun);
        Log.infof("Starting %d app-based transformer(s) for run %d", appTransformers.size(), run.getId());

        try {
            for (ConnectorTransformerDTO transformer : appTransformers) {
                forRun.put(keyOf(transformer, run.getId()),
                        start(transformer, run.getId(), run.getKeycloakId(), step -> {
                        }));
            }
        } catch (RuntimeException e) {
            close(run.getId());
            throw e;
        }
    }

    private TransformerSession start(ConnectorTransformerDTO transformer,
                                     Long connectorRunId,
                                     String keycloakId,
                                     Consumer<ConnectorRunStepDTO> onStepCreated) {
        ConnectorRunStepDTO step = new ConnectorRunStepDTO();
        step.setConnectorRunId(connectorRunId);
        step.setTransformationId(transformer.getId());
        step.setStatus(RunStatusTypes.PENDING);
        step.setProgress(0f);
        step.setHyperParams(appHyperParams(transformer));
        step = stepBO.createInNewTransaction(step);

        // Claim the step before the container can connect, so the websocket's own start-on-connect
        // does not consume the app's single worker slot with an empty run.
        sessionSteps.add(step.getId());
        onStepCreated.accept(step);

        ConnectorRunStepDTO started = executionRunBO.startModel(transformer, step, keycloakId);
        if (started == null || (started.getLastError() != null && !started.getLastError().isBlank())) {
            String error = started == null || started.getLastError() == null
                    ? "App transformer '" + transformer.getAppImage() + "' could not be started"
                    : started.getLastError();
            failStep(step.getId(), error);
            throw new AppTransformerSessionException(error);
        }

        awaitConnection(started.getId(), transformer);
        Log.infof("App transformer '%s' ready on step %d for run %s",
                transformer.getAppImage(), started.getId(), String.valueOf(connectorRunId));
        return new TransformerSession(started.getId(), transformer);
    }

    private void awaitConnection(Long stepId, ConnectorTransformerDTO transformer) {
        Log.infof("Waiting up to %ds for app transformer '%s' (step %d) to connect",
                CONNECT_TIMEOUT.toSeconds(), transformer.getAppImage(), stepId);
        long started = System.nanoTime();
        long deadline = started + CONNECT_TIMEOUT.toNanos();
        long nextLog = started + CONNECT_LOG_INTERVAL.toNanos();
        while (System.nanoTime() < deadline) {
            if (findConnection(stepId).isPresent()) {
                return;
            }
            // An app that connected and then dropped has already failed its step through the
            // websocket's close handling. Without this the session would sit out the whole timeout
            // waiting for a connection that has been and gone.
            RunStatusTypes status = stepStatus(stepId);
            if (status != null && RunStatusTypes.isFinalStatus(status)) {
                throw new AppTransformerSessionException("App transformer '" + transformer.getAppImage()
                        + "' ended as " + status + " before it could take any work"
                        + " - it connected and dropped, or failed to start.");
            }
            long now = System.nanoTime();
            if (now >= nextLog) {
                nextLog = now + CONNECT_LOG_INTERVAL.toNanos();
                Log.infof("Still waiting for app transformer '%s' (step %d) after %ds",
                        transformer.getAppImage(), stepId,
                        TimeUnit.NANOSECONDS.toSeconds(now - started));
            }
            try {
                Thread.sleep(CONNECT_POLL.toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AppTransformerSessionException("Interrupted while waiting for app transformer to connect");
            }
        }
        // The most common cause by far is an image built against a wrapper that predates API-key
        // auth: it starts, fails the websocket handshake, and stays up saying nothing, because its
        // own logs travel over the websocket it could not open.
        String error = "App transformer '" + transformer.getAppImage() + "' did not connect within "
                + CONNECT_TIMEOUT.toSeconds() + "s. The container started but never opened its"
                + " websocket - check that the image is built against a pyfedappwrap that supports"
                + " API-key authentication.";
        failStep(stepId, error);
        throw new AppTransformerSessionException(error);
    }

    private RunStatusTypes stepStatus(Long stepId) {
        try {
            return stepBO.statusOf(stepId);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Waits for the app's connection, which may be mid-reconnect rather than gone.
     *
     * <p>The wrapper reconnects on a timer whenever its socket drops, so between two batches the
     * connection can legitimately be absent for a few seconds. Treating that as a dead app failed
     * imports that would have recovered on their own.</p>
     */
    private WebSocketConnection awaitReconnect(Long stepId, ConnectorTransformerDTO transformer) {
        long deadline = System.nanoTime() + RECONNECT_TIMEOUT.toNanos();
        boolean waited = false;
        while (true) {
            Optional<WebSocketConnection> connection = findConnection(stepId);
            if (connection.isPresent()) {
                if (waited) {
                    Log.infof("App transformer '%s' (step %d) reconnected", transformer.getAppImage(), stepId);
                }
                return connection.get();
            }
            if (System.nanoTime() >= deadline) {
                throw new AppTransformerSessionException("App transformer '" + transformer.getAppImage()
                        + "' did not reconnect within " + RECONNECT_TIMEOUT.toSeconds() + "s");
            }
            if (!waited) {
                waited = true;
                Log.infof("App transformer '%s' (step %d) is not connected, waiting up to %ds for it to come back",
                        transformer.getAppImage(), stepId, RECONNECT_TIMEOUT.toSeconds());
            }
            try {
                Thread.sleep(CONNECT_POLL.toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AppTransformerSessionException("Interrupted while waiting for app transformer to reconnect");
            }
        }
    }

    private Optional<WebSocketConnection> findConnection(Long stepId) {
        return connections.findByEndpointId(ConnectorRunExecutionWebsocketService.class.getName())
                .stream()
                .filter(c -> Objects.equals(stepId, parseRunId(c)))
                .findFirst();
    }

    private static Long parseRunId(WebSocketConnection connection) {
        try {
            return Long.parseLong(connection.pathParam("runId"));
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * Runs one batch of rows through the transformer's already-running container.
     *
     * <p>The rows travel inline on the websocket: the app resolves an inline value before it
     * considers paths, so a batch never has to be staged anywhere first.</p>
     */
    public List<Map<String, Object>> transform(Long runId,
                                               ConnectorTransformerDTO transformer,
                                               List<Map<String, Object>> rows) {
        TransformerSession session = Optional.ofNullable(sessions.get(runId))
                .map(forRun -> forRun.get(keyOf(transformer, runId)))
                .orElseThrow(() -> new AppTransformerSessionException(
                        "No running app transformer for '" + transformer.getAppImage() + "' in run " + runId));

        WebSocketConnection connection = awaitReconnect(session.stepId(), transformer);

        Long batchId = batchIds.decrementAndGet();
        outputAwaiter.registerBatch(batchId);

        StartRunDTO start = new StartRunDTO();
        start.setId(batchId);
        start.setStatus(RunStatusTypes.PENDING);
        start.setHyperParams(appHyperParams(transformer));
        LinkedHashMap<String, Object> inputData = new LinkedHashMap<>();
        inputData.put(INPUT_VARIABLE, rows);
        start.setInputData(inputData);
        start.setInputFilePaths(new LinkedHashMap<>());

        try {
            connection.sendTextAndAwait(
                    new AppMessageWrapperDTO<>(AppMessageTypeEnum.START_PREDICTION, start, AppRunTypeEnum.MODEL_RUN));
            TableData output = outputAwaiter.awaitBatch(batchId, APP_OUTPUT_TIMEOUT).await().atMost(APP_OUTPUT_TIMEOUT);
            if (output == null) {
                throw new AppTransformerSessionException(
                        "App transformer '" + transformer.getAppImage() + "' returned no rows for a batch");
            }
            try (output) {
                return merge(rows, output.getRows(), transformer);
            }
        } catch (AppTransformerSessionException e) {
            outputAwaiter.cancelBatch(batchId);
            throw e;
        } catch (Exception e) {
            outputAwaiter.cancelBatch(batchId);
            throw new AppTransformerSessionException(
                    "App transformer '" + transformer.getAppImage() + "' failed on a batch: " + e.getMessage(), e);
        }
    }

    static List<Map<String, Object>> merge(List<Map<String, Object>> sent,
                                                   List<Map<String, Object>> returned,
                                                   ConnectorTransformerDTO transformer) {
        if (returned.size() != sent.size()) {
            Log.debugf("App transformer '%s' returned %d row(s) for %d sent; taking its rows as they are",
                    transformer.getAppImage(), returned.size(), sent.size());
            return new ArrayList<>(returned);
        }

        List<Map<String, Object>> merged = new ArrayList<>(sent.size());
        for (int index = 0; index < sent.size(); index++) {
            Map<String, Object> row = new LinkedHashMap<>(sent.get(index));
            row.putAll(returned.get(index));
            merged.add(row);
        }
        return merged;
    }

    /** Stops every container this run started, whether the run succeeded or failed. */
    public void close(Long runId) {
        Map<Long, TransformerSession> forRun = sessions.remove(runId);
        if (forRun == null) {
            return;
        }
        forRun.values().forEach(session -> {
            sessionSteps.remove(session.stepId());
            toolApiKeyService.revoke(Scope.CONNECTOR_RUN, session.stepId());
            try {
                // Marking the step finished also tears the container down.
                stepBO.updateAndNotify(session.stepId(), RunStatusTypes.FINISHED, null);
                Log.infof("Stopped app transformer '%s' (step %d) of run %d",
                        session.transformer().getAppImage(), session.stepId(), runId);
            } catch (Exception e) {
                Log.errorf("Could not stop app transformer step %d of run %d: %s",
                        session.stepId(), runId, e.getMessage());
            }
        });
    }

    private void failStep(Long stepId, String error) {
        sessionSteps.remove(stepId);
        try {
            stepBO.updateAndNotify(stepId, RunStatusTypes.ERROR, error);
        } catch (Exception e) {
            Log.errorf("Could not mark app transformer step %d as failed: %s", stepId, e.getMessage());
        }
    }

    /**
     * The hyper parameters the app is configured with, including the column mapping.
     *
     * <p>A transformer app is told which columns to read and write through {@code input_mapping},
     * {@code return_mapping} and {@code column}, which the wrapper reads off the run's hyper
     * parameters. The connector models those as first-class fields, so they are merged in here -
     * an explicit hyper parameter of the same name wins, so a connector can still say it directly.</p>
     */
    static LinkedHashMap<String, Object> appHyperParams(ConnectorTransformerDTO transformer) {
        LinkedHashMap<String, Object> params = new LinkedHashMap<>();
        if (transformer.getInputMapping() != null && !transformer.getInputMapping().isEmpty()) {
            params.put("input_mapping", transformer.getInputMapping());
        }
        if (transformer.getReturnMapping() != null && !transformer.getReturnMapping().isEmpty()) {
            params.put("return_mapping", transformer.getReturnMapping());
        }
        if (transformer.getColumn() != null && !transformer.getColumn().isBlank()) {
            params.put("column", transformer.getColumn());
        }
        if (transformer.getHyperparams() != null) {
            params.putAll(transformer.getHyperparams());
        }
        return params;
    }

    /** Name of the app input a transformer reads its rows from; fixed by the transformer contract. */
    private static final String INPUT_VARIABLE = "input";

    public static List<ConnectorTransformerDTO> appTransformersOf(ConnectorDTO connector) {
        if (connector == null || connector.getTransformer() == null) {
            return List.of();
        }
        return connector.getTransformer().stream()
                .filter(Objects::nonNull)
                .filter(t -> t.getAppImage() != null && !t.getAppImage().isBlank())
                .toList();
    }

    public static class AppTransformerSessionException extends RuntimeException {
        public AppTransformerSessionException(String message) {
            super(message);
        }

        public AppTransformerSessionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
