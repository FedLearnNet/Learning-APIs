package bio.cosy.feddb.local.api.importer.transformer.code.runner;

import bio.cosy.feddb.local.api.importer.transformer.ConnectorTransformerDTO;
import bio.cosy.feddb.local.api.importer.transformer.code.CodeTransformerLogDTO;
import bio.cosy.feddb.local.api.importer.transformer.session.AppTransformerSessionBO;
import bio.cosy.feddb.local.config.FLNetClientConfig;
import io.quarkus.scheduler.Scheduled;
import io.quarkus.websockets.next.WebSocketConnection;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.operators.multi.processors.BroadcastProcessor;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Duration;
import java.time.Instant;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;

@ApplicationScoped
public class CodeTransformerContainerBO {

    private static final Duration IDLE_TIMEOUT = Duration.ofMinutes(15);
    private static final int LOG_LINES = 500;
    private final Deque<CodeTransformerLogDTO> logs = new ConcurrentLinkedDeque<>();
    private final BroadcastProcessor<CodeTransformerLogDTO> logStream = BroadcastProcessor.create();
    private volatile CodeTransformerRunnerStatus status = CodeTransformerRunnerStatus.STOPPED;
    private volatile String error;
    private Long sessionId;
    private volatile Long stepId;
    private Instant lastUsed;

    @Inject
    FLNetClientConfig config;

    @Inject
    AppTransformerSessionBO sessionBO;

    public CodeTransformerRunnerDTO status() {
        return new CodeTransformerRunnerDTO(status, image(), error);
    }

    public WebSocketConnection connection(String keycloakId) {
        return sessionBO.connection(start(keycloakId));
    }

    public synchronized Long start(String keycloakId) {
        lastUsed = Instant.now();
        if (sessionId != null) {
            return sessionId;
        }
        status = CodeTransformerRunnerStatus.STARTING;
        error = null;
        log("Starting the code runner " + image());
        ConnectorTransformerDTO runner = new ConnectorTransformerDTO();
        runner.setAppImage(image());
        try {
            sessionId = sessionBO.openPreview(runner, keycloakId, step -> stepId = step.getId());
            status = CodeTransformerRunnerStatus.RUNNING;
            log("The code runner is ready");
            return sessionId;
        } catch (RuntimeException e) {
            status = CodeTransformerRunnerStatus.FAILED;
            error = e.getMessage();
            log("The code runner could not be started: " + error);
            throw e;
        }
    }

    public synchronized void stop() {
        if (sessionId != null) {
            sessionBO.close(sessionId);
            sessionId = null;
            stepId = null;
            status = CodeTransformerRunnerStatus.STOPPED;
            log("The code runner was stopped");
        }
    }

    public void log(Long runId, String message) {
        if (runId != null && runId.equals(stepId) && message != null && !message.isBlank()) {
            log(message);
        }
    }

    public Multi<CodeTransformerLogDTO> logs() {
        return Multi.createBy().concatenating().streams(Multi.createFrom().iterable(List.copyOf(logs)), logStream);
    }

    @Scheduled(every = "1m")
    synchronized void stopIdle() {
        if (sessionId != null && lastUsed.plus(IDLE_TIMEOUT).isBefore(Instant.now())) {
            log("Stopping the code runner after " + IDLE_TIMEOUT.toMinutes() + " idle minutes");
            stop();
        }
    }

    private String image() {
        return config.connector().codeTransformerImage();
    }

    void log(String message) {
        CodeTransformerLogDTO line = new CodeTransformerLogDTO(Instant.now(), message.strip());
        logs.addLast(line);
        while (logs.size() > LOG_LINES) {
            logs.pollFirst();
        }
        logStream.onNext(line);
    }
}
