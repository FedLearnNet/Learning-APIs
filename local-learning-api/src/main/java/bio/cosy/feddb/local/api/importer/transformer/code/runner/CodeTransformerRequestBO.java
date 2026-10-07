package bio.cosy.feddb.local.api.importer.transformer.code.runner;

import bio.cosy.feddb.core.api.run.AppMessageTypeEnum;
import bio.cosy.feddb.core.api.run.AppMessageWrapperDTO;
import bio.cosy.feddb.core.api.run.AppRunTypeEnum;
import bio.cosy.feddb.local.api.importer.transformer.code.check.CodeTransformerCheckDTO;
import bio.cosy.feddb.local.api.importer.transformer.code.check.CodeTransformerCheckResultDTO;
import bio.cosy.feddb.local.api.importer.transformer.code.save.CodeTransformerSaveDTO;
import bio.cosy.feddb.local.api.importer.transformer.code.save.CodeTransformerSaveResultDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.function.Function;

@ApplicationScoped
public class CodeTransformerRequestBO {

    private static final Duration ANSWER_TIMEOUT = Duration.ofMinutes(1);
    private final Map<String, CompletableFuture<Object>> pending = new ConcurrentHashMap<>();

    @Inject
    CodeTransformerContainerBO containerBO;

    @Inject
    ObjectMapper objectMapper;

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
        CompletableFuture<Object> answer = pending.get(requestId);
        if (answer != null) {
            answer.complete(message);
        }
    }

    private <T> T ask(AppMessageTypeEnum type, CodeTransformerCheckDTO request, String keycloakId,
                      Class<T> resultType, Function<String, T> failed) {
        request.setRequestId(UUID.randomUUID().toString());
        CompletableFuture<Object> answer = new CompletableFuture<>();
        pending.put(request.getRequestId(), answer);
        try {
            containerBO.connection(keycloakId).sendTextAndAwait(
                    new AppMessageWrapperDTO<>(type, request, AppRunTypeEnum.MODEL_RUN));
            return objectMapper.convertValue(answer.get(ANSWER_TIMEOUT.toSeconds(), TimeUnit.SECONDS), resultType);
        } catch (TimeoutException e) {
            return failed.apply("The code runner did not answer within " + ANSWER_TIMEOUT.toSeconds() + "s");
        } catch (InterruptedException | ExecutionException | RuntimeException e) {
            containerBO.log("Code runner request failed: " + e.getMessage());
            containerBO.stop();
            return failed.apply(e.getMessage());
        } finally {
            pending.remove(request.getRequestId());
        }
    }
}
