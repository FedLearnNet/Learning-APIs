package bio.cosy.feddb.local.api.importer.transformer.session;

import bio.cosy.feddb.core.api.run.AppMessageTypeEnum;
import bio.cosy.feddb.core.api.run.AppMessageWrapperDTO;
import bio.cosy.feddb.core.api.run.AppRunTypeEnum;
import bio.cosy.feddb.core.api.run.RunStatusTypes;
import bio.cosy.feddb.core.security.Scope;
import bio.cosy.feddb.core.security.ToolApiKeyService;
import bio.cosy.feddb.local.api.importer.connector.ConnectorDTO;
import bio.cosy.feddb.local.api.importer.files.table.TableData;
import bio.cosy.feddb.local.api.importer.run.ConnectorRunDTO;
import bio.cosy.feddb.local.api.importer.run.execution.ConnectorRunExecutionOutputAwaiter;
import bio.cosy.feddb.local.api.importer.run.execution.ConnectorRunExecutionRunBO;
import bio.cosy.feddb.local.api.importer.run.step.ConnectorRunStepBO;
import bio.cosy.feddb.local.api.importer.run.step.ConnectorRunStepDTO;
import bio.cosy.feddb.local.api.importer.transformer.ConnectorTransformerDTO;
import io.quarkus.logging.Log;
import io.smallrye.mutiny.TimeoutException;
import io.quarkus.websockets.next.WebSocketConnection;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import static bio.cosy.feddb.local.api.importer.run.execution.ConnectorRunExecutionOutputAwaiter.APP_OUTPUT_TIMEOUT;

// One running container per app-based transformer for the length of a run; every batch is another run on it
@ApplicationScoped
public class AppTransformerSessionBO {

    @Inject
    ConnectorRunExecutionRunBO executionRunBO;

    @Inject
    ConnectorRunStepBO stepBO;

    @Inject
    ToolApiKeyService toolApiKeyService;

    @Inject
    ConnectorRunExecutionOutputAwaiter outputAwaiter;

    @Inject
    AppTransformerConnectionBO connectionBO;

    @Inject
    AppTransformerMapper mapper;

    private final Map<Long, Map<Long, AppTransformerSession>> sessions = new ConcurrentHashMap<>();
    private final Set<Long> sessionSteps = ConcurrentHashMap.newKeySet();
    private final AtomicLong batchIds = new AtomicLong();
    private final AtomicLong previewSessionIds = new AtomicLong();

    public boolean isSessionStep(Long stepId) {
        return stepId != null && sessionSteps.contains(stepId);
    }

    public Long openPreview(ConnectorTransformerDTO transformer, String keycloakId,
                            Consumer<ConnectorRunStepDTO> onStepCreated) {
        Long sessionId = previewSessionIds.decrementAndGet();
        Log.infof("Starting app transformer '%s' for a preview", transformer.getAppImage());
        openSessions(sessionId, List.of(transformer), null, keycloakId, onStepCreated);
        return sessionId;
    }

    public void open(ConnectorDTO connector, ConnectorRunDTO run) {
        List<ConnectorTransformerDTO> appTransformers = mapper.appTransformersOf(connector);
        if (appTransformers.isEmpty()) {
            return;
        }
        Log.infof("Starting %d app-based transformer(s) for run %d", appTransformers.size(), run.getId());
        openSessions(run.getId(), appTransformers, run.getId(), run.getKeycloakId(), step -> {
        });
    }

    public List<Map<String, Object>> transform(Long runId, ConnectorTransformerDTO transformer,
                                               List<Map<String, Object>> rows) {
        AppTransformerSession session = findSession(runId, transformer);
        WebSocketConnection connection = connectionBO.awaitReconnect(session.getStepId(), transformer.getAppImage());
        Long batchId = batchIds.decrementAndGet();
        outputAwaiter.registerBatch(batchId);
        try {
            connection.sendTextAndAwait(new AppMessageWrapperDTO<>(AppMessageTypeEnum.START_PREDICTION,
                    mapper.toBatch(batchId, transformer, rows), AppRunTypeEnum.MODEL_RUN));
            try (TableData output = awaitOutput(batchId, transformer)) {
                return mapper.merge(rows, output.getRows(), transformer);
            }
        } catch (AppTransformerSessionException e) {
            outputAwaiter.cancelBatch(batchId);
            throw e;
        } catch (Exception e) {
            outputAwaiter.cancelBatch(batchId);
            throw new AppTransformerSessionException(
                    "App transformer '" + transformer.getAppImage() + "' failed on a batch: " + reason(e), e);
        }
    }

    public WebSocketConnection connection(Long sessionId) {
        AppTransformerSession session = sessions.getOrDefault(sessionId, Map.of()).values().stream().findFirst()
                .orElseThrow(() -> new AppTransformerSessionException("No running app transformer for session " + sessionId));
        return connectionBO.awaitReconnect(session.getStepId(), session.getTransformer().getAppImage());
    }

    public void close(Long runId) {
        Optional.ofNullable(sessions.remove(runId))
                .ifPresent(forRun -> forRun.values().forEach(session -> stop(session, runId)));
    }

    @PreDestroy
    public void closeAll() {
        List.copyOf(sessions.keySet()).forEach(this::close);
    }

    private void openSessions(Long sessionId, List<ConnectorTransformerDTO> transformers, Long connectorRunId,
                              String keycloakId, Consumer<ConnectorRunStepDTO> onStepCreated) {
        Map<Long, AppTransformerSession> forSession = new ConcurrentHashMap<>();
        sessions.put(sessionId, forSession);
        try {
            for (ConnectorTransformerDTO transformer : transformers) {
                forSession.put(keyOf(transformer, sessionId), start(transformer, connectorRunId, keycloakId, onStepCreated));
            }
        } catch (RuntimeException e) {
            close(sessionId);
            throw e;
        }
    }

    private AppTransformerSession start(ConnectorTransformerDTO transformer, Long connectorRunId, String keycloakId,
                                        Consumer<ConnectorRunStepDTO> onStepCreated) {
        ConnectorRunStepDTO step = stepBO.createInNewTransaction(mapper.toStep(transformer, connectorRunId));
        // Claimed before the container connects, so the websocket does not start an empty run on the app's only worker
        sessionSteps.add(step.getId());
        onStepCreated.accept(step);

        ConnectorRunStepDTO started = executionRunBO.startModel(transformer, step, keycloakId);
        if (started == null || started.getLastError() != null && !started.getLastError().isBlank()) {
            fail(step.getId(), started == null || started.getLastError() == null
                    ? "App transformer '" + transformer.getAppImage() + "' could not be started"
                    : started.getLastError());
        }
        if (!connectionBO.awaitConnection(started.getId(), transformer.getAppImage())) {
            fail(started.getId(), connectTimeoutMessage(transformer));
        }
        Log.infof("App transformer '%s' ready on step %d for run %s", transformer.getAppImage(), started.getId(),
                connectorRunId);
        return new AppTransformerSession(started.getId(), transformer);
    }

    private AppTransformerSession findSession(Long runId, ConnectorTransformerDTO transformer) {
        return Optional.ofNullable(sessions.get(runId))
                .map(forRun -> forRun.get(keyOf(transformer, runId)))
                .orElseThrow(() -> new AppTransformerSessionException(
                        "No running app transformer for '" + transformer.getAppImage() + "' in run " + runId));
    }

    private TableData awaitOutput(Long batchId, ConnectorTransformerDTO transformer) {
        TableData output = outputAwaiter.awaitBatch(batchId, APP_OUTPUT_TIMEOUT).await().atMost(APP_OUTPUT_TIMEOUT);
        if (output == null) {
            throw new AppTransformerSessionException(
                    "App transformer '" + transformer.getAppImage() + "' returned no rows for a batch");
        }
        return output;
    }

    // Marking the step finished also tears the container down
    private void stop(AppTransformerSession session, Long runId) {
        sessionSteps.remove(session.getStepId());
        toolApiKeyService.revoke(Scope.CONNECTOR_RUN, session.getStepId());
        try {
            stepBO.updateAndNotify(session.getStepId(), RunStatusTypes.FINISHED, null);
            Log.infof("Stopped app transformer '%s' (step %d) of run %d",
                    session.getTransformer().getAppImage(), session.getStepId(), runId);
        } catch (Exception e) {
            Log.errorf("Could not stop app transformer step %d of run %d: %s", session.getStepId(), runId, e.getMessage());
        }
    }

    private void fail(Long stepId, String error) {
        sessionSteps.remove(stepId);
        try {
            stepBO.updateAndNotify(stepId, RunStatusTypes.ERROR, error);
        } catch (Exception e) {
            Log.errorf("Could not mark app transformer step %d as failed: %s", stepId, e.getMessage());
        }
        throw new AppTransformerSessionException(error);
    }

    private static String reason(Exception exception) {
        if (exception instanceof TimeoutException) {
            return "no result within " + APP_OUTPUT_TIMEOUT.toMinutes() + " minutes";
        }
        Throwable cause = exception;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause.getMessage();
    }

    private static Long keyOf(ConnectorTransformerDTO transformer, Long sessionId) {
        return transformer.getId() != null ? transformer.getId() : sessionId;
    }

    // Mostly an image built against a wrapper without API-key auth: it starts, fails the handshake and stays silent
    private static String connectTimeoutMessage(ConnectorTransformerDTO transformer) {
        return "App transformer '" + transformer.getAppImage() + "' did not connect in time. The container started"
                + " but never opened its websocket - check that the image is built against a pyfedappwrap that"
                + " supports API-key authentication.";
    }
}
