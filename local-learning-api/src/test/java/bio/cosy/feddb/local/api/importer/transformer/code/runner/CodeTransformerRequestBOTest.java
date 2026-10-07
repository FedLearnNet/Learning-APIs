package bio.cosy.feddb.local.api.importer.transformer.code.runner;

import bio.cosy.feddb.core.api.run.AppMessageTypeEnum;
import bio.cosy.feddb.core.api.run.AppMessageWrapperDTO;
import bio.cosy.feddb.local.api.importer.transformer.code.check.CodeTransformerCheckDTO;
import bio.cosy.feddb.local.api.importer.transformer.code.check.CodeTransformerCheckResultDTO;
import bio.cosy.feddb.local.api.importer.transformer.session.AppTransformerSessionException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.websockets.next.WebSocketConnection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CodeTransformerRequestBOTest {

    private final CodeTransformerRequestBO requestBO = new CodeTransformerRequestBO();
    private final CodeTransformerContainerBO containerBO = mock(CodeTransformerContainerBO.class);
    private final WebSocketConnection connection = mock(WebSocketConnection.class);

    @BeforeEach
    void setUp() {
        requestBO.containerBO = containerBO;
        requestBO.objectMapper = new ObjectMapper();
        when(containerBO.connection("user")).thenReturn(connection);
    }

    @Test
    void checkReturnsTheRunnerResult() {
        doAnswer(invocation -> {
            AppMessageWrapperDTO<CodeTransformerCheckDTO> sent = invocation.getArgument(0);
            assertEquals(AppMessageTypeEnum.TRANSFORMATION_CHECK, sent.getType());
            requestBO.complete(Map.of("requestId", sent.getMessage().getRequestId(), "isSuccess", true,
                    "message", "ok", "inputKeys", List.of("a")));
            return null;
        }).when(connection).sendTextAndAwait(any(AppMessageWrapperDTO.class));

        CodeTransformerCheckResultDTO result = requestBO.check(new CodeTransformerCheckDTO(), "user");

        assertTrue(result.getIsSuccess());
        assertEquals(List.of("a"), result.getInputKeys());
    }

    @Test
    void failingRunnerIsReportedAndStopped() {
        when(containerBO.connection("user")).thenThrow(new AppTransformerSessionException("gone"));

        CodeTransformerCheckResultDTO result = requestBO.check(new CodeTransformerCheckDTO(), "user");

        assertFalse(result.getIsSuccess());
        assertEquals("gone", result.getMessage());
        verify(containerBO).stop();
        verify(connection, never()).sendTextAndAwait(any(AppMessageWrapperDTO.class));
    }
}
