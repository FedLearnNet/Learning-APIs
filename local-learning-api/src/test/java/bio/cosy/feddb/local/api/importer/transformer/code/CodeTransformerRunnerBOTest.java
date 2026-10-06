package bio.cosy.feddb.local.api.importer.transformer.code;

import bio.cosy.feddb.core.api.run.AppMessageTypeEnum;
import bio.cosy.feddb.core.api.run.AppMessageWrapperDTO;
import bio.cosy.feddb.local.api.importer.run.step.ConnectorRunStepDTO;
import bio.cosy.feddb.local.api.importer.transformer.AppTransformerSessionBO;
import bio.cosy.feddb.local.api.importer.transformer.code.check.CodeTransformerCheckDTO;
import bio.cosy.feddb.local.api.importer.transformer.code.check.CodeTransformerCheckResultDTO;
import bio.cosy.feddb.local.api.importer.transformer.code.runner.CodeTransformerRunnerBO;
import bio.cosy.feddb.local.api.importer.transformer.code.runner.CodeTransformerRunnerDTO;
import bio.cosy.feddb.local.config.FLNetClientConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.websockets.next.WebSocketConnection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CodeTransformerRunnerBOTest {

    private final CodeTransformerRunnerBO codeTransformerBO = new CodeTransformerRunnerBO();
    private final AppTransformerSessionBO sessionBO = mock(AppTransformerSessionBO.class);
    private final WebSocketConnection connection = mock(WebSocketConnection.class);

    @BeforeEach
    void setUp() {
        codeTransformerBO.config = mock(FLNetClientConfig.class, RETURNS_DEEP_STUBS);
        when(codeTransformerBO.config.connector().codeTransformerImage()).thenReturn("runner");
        codeTransformerBO.sessionBO = sessionBO;
        codeTransformerBO.objectMapper = new ObjectMapper();
        when(sessionBO.openPreview(any(), eq("user"), any())).thenReturn(-1L);
        when(sessionBO.connection(-1L)).thenReturn(connection);
    }

    @Test
    void checkReturnsTheRunnerResultAndReusesTheRunner() {
        doAnswer(invocation -> {
            AppMessageWrapperDTO<CodeTransformerCheckDTO> sent = invocation.getArgument(0);
            assertEquals(AppMessageTypeEnum.TRANSFORMATION_CHECK, sent.getType());
            codeTransformerBO.complete(Map.of("requestId", sent.getMessage().getRequestId(), "isSuccess", true,
                    "message", "ok", "inputKeys", List.of("a")));
            return null;
        }).when(connection).sendTextAndAwait(any(AppMessageWrapperDTO.class));

        CodeTransformerCheckResultDTO first = codeTransformerBO.check(new CodeTransformerCheckDTO(), "user");
        codeTransformerBO.check(new CodeTransformerCheckDTO(), "user");

        assertTrue(first.getIsSuccess());
        assertEquals(List.of("a"), first.getInputKeys());
        verify(sessionBO).openPreview(any(), eq("user"), any());
    }

    @Test
    void failingRunnerIsReportedAndStopped() {
        when(sessionBO.connection(-1L)).thenThrow(new AppTransformerSessionBO.AppTransformerSessionException("gone"));

        CodeTransformerCheckResultDTO result = codeTransformerBO.check(new CodeTransformerCheckDTO(), "user");

        assertFalse(result.getIsSuccess());
        assertEquals("gone", result.getMessage());
        verify(sessionBO).close(-1L);
        verify(connection, never()).sendTextAndAwait(any(AppMessageWrapperDTO.class));
    }

    @Test
    void runnerLogKeepsLifecycleAndOwnStepLinesOnly() {
        when(sessionBO.openPreview(any(), eq("user"), any())).thenAnswer(invocation -> {
            ConnectorRunStepDTO step = new ConnectorRunStepDTO();
            step.setId(5L);
            invocation.<Consumer<ConnectorRunStepDTO>>getArgument(2).accept(step);
            return -1L;
        });

        codeTransformerBO.start("user");
        codeTransformerBO.log(5L, "from the runner");
        codeTransformerBO.log(6L, "from another app");
        codeTransformerBO.stop();

        List<String> lines = codeTransformerBO.logs().select().first(4).collect().asList().await().indefinitely()
                .stream().map(CodeTransformerLogDTO::getMessage).toList();
        assertEquals(List.of("Starting the code runner runner", "The code runner is ready", "from the runner",
                "The code runner was stopped"), lines);
        assertEquals(CodeTransformerRunnerDTO.Status.STOPPED, codeTransformerBO.status().getStatus());
    }
}
