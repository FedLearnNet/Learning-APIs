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
import java.util.concurrent.*;
import java.util.function.Function;

@ApplicationScoped
public class CodeTransformerRunnerBO {

    private static final Duration ANSWER_TIMEOUT = Duration.ofMinutes(1);
    private static final Duration IDLE_TIMEOUT = Duration.ofMinutes(15);
    private static final int LOG_LINES = 500;

    @Inject
    FLNetClientConfig config;

    @Inject
    AppTransformerSessionBO sessionBO;

    @Inject
    ObjectMapper objectMapper;

    private final Map<String, CompletableFuture<Object>> pending = new ConcurrentHashMap<>();
    private final Deque<CodeTransformerLogDTO> logs = new ConcurrentLinkedDeque<>();
    private final BroadcastProcessor<CodeTransformerLogDTO> logStream = BroadcastProcessor.create();
    private volatile CodeTransformerRunnerDTO.Status status = CodeTransformerRunnerDTO.Status.STOPPED;
    private volatile String error;
    private volatile Long sessionId;
    private volatile Long stepId;
    private Instant lastUsed = Instant.now();

    public CodeTransformerCheckResultDTO check(CodeTransformerCheckDTO request, String keycloakId) {
        return ask(AppMessageTypeEnum.TRANSFORMATION_CHECK, request, keycloakId, CodeTransformerCheckResultDTO.class,
                message -> CodeTransformerCheckResultDTO.failed(request.getRequestId(), message));
    }

    public CodeTransformerSaveResultDTO generate(CodeTransformerSaveDTO request, String keycloakId) {
        return ask(AppMessageTypeEnum.TRANSFORMATION_SAVE, request, keycloakId, CodeTransformerSaveResultDTO.class,
                message -> CodeTransformerSaveResultDTO.failed(request.getRequestId(), message));
    }

    public void complete(Object message) {
        String requestId = objectMapper.valueToTree(message).path("requestId").asText();
        Optional.ofNullable(pending.get(requestId)).ifPresent(future -> future.complete(message));
    }

    private <T> T ask(AppMessageTypeEnum type, CodeTransformerCheckDTO request, String keycloakId,
                      Class<T> resultType, Function<String, T> failed) {
        request.setRequestId(UUID.randomUUID().toString());
        CompletableFuture<Object> answer = new CompletableFuture<>();
        pending.put(request.getRequestId(), answer);
        try {
            sessionBO.connection(start(keycloakId)).sendTextAndAwait(
                    new AppMessageWrapperDTO<>(type, request, AppRunTypeEnum.MODEL_RUN));
            return objectMapper.convertValue(answer.get(ANSWER_TIMEOUT.toSeconds(), TimeUnit.SECONDS), resultType);
        } catch (TimeoutException e) {
            return failed.apply("The code runner did not answer within " + ANSWER_TIMEOUT.toSeconds() + "s");
        } catch (InterruptedException | ExecutionException | RuntimeException e) {
            log("Code runner request failed: " + e.getMessage());
            stop();
            return failed.apply(e.getMessage());
        } finally {
            pending.remove(request.getRequestId());
        }
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
