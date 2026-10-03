package bio.cosy.feddb.local.api.importer.transformer.code;

import bio.cosy.feddb.core.api.run.AppMessageTypeEnum;
import bio.cosy.feddb.core.api.run.AppMessageWrapperDTO;
import bio.cosy.feddb.core.api.run.AppRunTypeEnum;
import bio.cosy.feddb.local.api.importer.transformer.AppTransformerSessionBO;
import bio.cosy.feddb.local.api.importer.transformer.ConnectorTransformerDTO;
import bio.cosy.feddb.local.config.FLNetClientConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.scheduler.Scheduled;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.operators.multi.processors.BroadcastProcessor;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.TimeUnit;

@ApplicationScoped
public class CodeTransformerBO {

    private static final Duration CHECK_TIMEOUT = Duration.ofMinutes(1);
    private static final Duration IDLE_TIMEOUT = Duration.ofMinutes(15);
    private static final int LOG_LINES = 500;

    @Inject
    FLNetClientConfig config;

    @Inject
    AppTransformerSessionBO sessionBO;

    @Inject
    ObjectMapper objectMapper;

    private final Map<String, CompletableFuture<CodeTransformerCheckResultDTO>> pending = new ConcurrentHashMap<>();
    private final Deque<CodeTransformerLogDTO> logs = new ConcurrentLinkedDeque<>();
    private final BroadcastProcessor<CodeTransformerLogDTO> logStream = BroadcastProcessor.create();
    private volatile CodeTransformerRunnerDTO.Status status = CodeTransformerRunnerDTO.Status.STOPPED;
    private volatile String error;
    private volatile Long sessionId;
    private volatile Long stepId;
    private Instant lastUsed = Instant.now();

    public CodeTransformerCheckResultDTO check(CodeTransformerCheckDTO request, String keycloakId) {
        String requestId = UUID.randomUUID().toString();
        request.setRequestId(requestId);
        CompletableFuture<CodeTransformerCheckResultDTO> result = new CompletableFuture<>();
        pending.put(requestId, result);
        try {
            sessionBO.connection(start(keycloakId)).sendTextAndAwait(
                    new AppMessageWrapperDTO<>(AppMessageTypeEnum.TRANSFORMATION_CHECK, request, AppRunTypeEnum.MODEL_RUN));
            return result.completeOnTimeout(CodeTransformerCheckResultDTO.failed(requestId,
                            "The code check did not answer within " + CHECK_TIMEOUT.toSeconds() + "s"),
                    CHECK_TIMEOUT.toSeconds(), TimeUnit.SECONDS).join();
        } catch (RuntimeException e) {
            log("Code check failed: " + e.getMessage());
            stop();
            return CodeTransformerCheckResultDTO.failed(requestId, e.getMessage());
        } finally {
            pending.remove(requestId);
        }
    }

    public void complete(Object message) {
        CodeTransformerCheckResultDTO result = objectMapper.convertValue(message, CodeTransformerCheckResultDTO.class);
        Optional.ofNullable(pending.get(result.getRequestId())).ifPresent(future -> future.complete(result));
    }

    public CodeTransformerRunnerDTO status() {
        return new CodeTransformerRunnerDTO(status, config.connector().codeTransformerImage(), error);
    }

    public synchronized Long start(String keycloakId) {
        lastUsed = Instant.now();
        if (sessionId == null) {
            status = CodeTransformerRunnerDTO.Status.STARTING;
            error = null;
            log("Starting the code runner " + config.connector().codeTransformerImage());
            ConnectorTransformerDTO runner = new ConnectorTransformerDTO();
            runner.setAppImage(config.connector().codeTransformerImage());
            try {
                sessionId = sessionBO.openPreview(runner, keycloakId, step -> stepId = step.getId());
            } catch (RuntimeException e) {
                status = CodeTransformerRunnerDTO.Status.FAILED;
                error = e.getMessage();
                log("The code runner could not be started: " + e.getMessage());
                throw e;
            }
            status = CodeTransformerRunnerDTO.Status.RUNNING;
            log("The code runner is ready");
        }
        return sessionId;
    }

    public synchronized void stop() {
        if (sessionId != null) {
            sessionBO.close(sessionId);
            sessionId = null;
            stepId = null;
            status = CodeTransformerRunnerDTO.Status.STOPPED;
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

    private void log(String message) {
        CodeTransformerLogDTO line = new CodeTransformerLogDTO(Instant.now(), message.strip());
        logs.addLast(line);
        while (logs.size() > LOG_LINES) {
            logs.pollFirst();
        }
        logStream.onNext(line);
    }
}
