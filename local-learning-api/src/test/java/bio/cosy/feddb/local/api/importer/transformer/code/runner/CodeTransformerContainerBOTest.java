package bio.cosy.feddb.local.api.importer.transformer.code.runner;

import bio.cosy.feddb.local.api.importer.run.step.ConnectorRunStepDTO;
import bio.cosy.feddb.local.api.importer.transformer.code.CodeTransformerLogDTO;
import bio.cosy.feddb.local.api.importer.transformer.session.AppTransformerSessionBO;
import bio.cosy.feddb.local.config.FLNetClientConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CodeTransformerContainerBOTest {

    private final CodeTransformerContainerBO containerBO = new CodeTransformerContainerBO();
    private final AppTransformerSessionBO sessionBO = mock(AppTransformerSessionBO.class);

    @BeforeEach
    void setUp() {
        containerBO.config = mock(FLNetClientConfig.class, RETURNS_DEEP_STUBS);
        when(containerBO.config.connector().codeTransformerImage()).thenReturn("runner");
        containerBO.sessionBO = sessionBO;
        when(sessionBO.openPreview(any(), eq("user"), any())).thenReturn(-1L);
    }

    @Test
    void startReusesTheRunningContainer() {
        containerBO.start("user");
        containerBO.start("user");

        verify(sessionBO).openPreview(any(), eq("user"), any());
        assertEquals(CodeTransformerRunnerStatus.RUNNING, containerBO.status().getStatus());
    }

    @Test
    void runnerLogKeepsLifecycleAndOwnStepLinesOnly() {
        when(sessionBO.openPreview(any(), eq("user"), any())).thenAnswer(invocation -> {
            ConnectorRunStepDTO step = new ConnectorRunStepDTO();
            step.setId(5L);
            invocation.<Consumer<ConnectorRunStepDTO>>getArgument(2).accept(step);
            return -1L;
        });

        containerBO.start("user");
        containerBO.log(5L, "from the runner");
        containerBO.log(6L, "from another app");
        containerBO.stop();

        List<String> lines = containerBO.logs().select().first(4).collect().asList().await().indefinitely()
                .stream().map(CodeTransformerLogDTO::getMessage).toList();
        assertEquals(List.of("Starting the code runner runner", "The code runner is ready", "from the runner",
                "The code runner was stopped"), lines);
        assertEquals(CodeTransformerRunnerStatus.STOPPED, containerBO.status().getStatus());
        verify(sessionBO).close(-1L);
    }
}
