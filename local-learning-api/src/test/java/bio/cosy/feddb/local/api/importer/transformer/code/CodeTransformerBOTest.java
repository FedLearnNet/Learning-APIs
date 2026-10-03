package bio.cosy.feddb.local.api.importer.transformer.code;

import bio.cosy.feddb.core.api.pipeline.PipelineStatus;
import bio.cosy.feddb.local.api.importer.transformer.ConnectorTransformerAO;
import bio.cosy.feddb.local.api.pipeline.PipelineEntity;
import bio.cosy.feddb.local.services.orch.OrchDockerServiceClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class CodeTransformerBOTest {

    private static final String OLD = "fl-net/code/transformer-3:old";

    private final CodeTransformerBO codeTransformerBO = new CodeTransformerBO();
    private final CodeTransformerEntity entity = new CodeTransformerEntity();

    @BeforeEach
    void setUp() {
        codeTransformerBO.connectorTransformerAO = mock(ConnectorTransformerAO.class);
        codeTransformerBO.dockerClient = mock(OrchDockerServiceClient.class);
        entity.setId(3L);
        entity.setCodeHash("new");
        entity.setImageName(OLD);
        entity.setStatus(CodeTransformerStatus.BUILDING);
    }

    private PipelineEntity pipeline(PipelineStatus status) {
        PipelineEntity pipeline = new PipelineEntity();
        pipeline.setCodeTransformer(entity);
        pipeline.setDockerTag("new");
        pipeline.setPipelineStatus(status);
        return pipeline;
    }

    @Test
    void successfulBuildReplacesTheImageEverywhere() {
        codeTransformerBO.finished(pipeline(PipelineStatus.SUCCESS));

        assertEquals("fl-net/code/transformer-3:new", entity.getImageName());
        assertEquals(CodeTransformerStatus.READY, entity.getStatus());
        verify(codeTransformerBO.connectorTransformerAO).replaceAppImage(OLD, "fl-net/code/transformer-3:new");
        verify(codeTransformerBO.dockerClient).removeLocalImage(OLD);
    }

    @Test
    void failedBuildKeepsTheWorkingImage() {
        codeTransformerBO.finished(pipeline(PipelineStatus.FAILED));

        assertEquals(OLD, entity.getImageName());
        assertEquals(CodeTransformerStatus.FAILED, entity.getStatus());
        verify(codeTransformerBO.connectorTransformerAO, never()).replaceAppImage(anyString(), anyString());
        verify(codeTransformerBO.dockerClient, never()).removeLocalImage(anyString());
    }
}
