package bio.cosy.feddb.local.api.importer.transformer.code;

import bio.cosy.feddb.core.api.pipeline.PipelineStatus;
import bio.cosy.feddb.local.api.importer.transformer.code.save.CodeTransformerImageSaveBO;
import bio.cosy.feddb.local.api.pipeline.PipelineEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class CodeTransformerBOTest {

    private final CodeTransformerBO codeTransformerBO = new CodeTransformerBO();
    private final CodeTransformerEntity entity = new CodeTransformerEntity();

    @BeforeEach
    void setUp() {
        codeTransformerBO.transformerImageBO = mock(CodeTransformerImageSaveBO.class);
        entity.setId(3L);
        entity.setCodeHash("new");
        entity.setImageName("fl-net/code/transformer-3:old");
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
    void successfulBuildSavesTheImage() {
        codeTransformerBO.finished(pipeline(PipelineStatus.SUCCESS));

        verify(codeTransformerBO.transformerImageBO).saveImage(entity, "new", true);
    }

    @Test
    void failedBuildKeepsTheWorkingImage() {
        codeTransformerBO.finished(pipeline(PipelineStatus.FAILED));

        assertEquals(CodeTransformerStatus.FAILED, entity.getStatus());
        verify(codeTransformerBO.transformerImageBO, never()).saveImage(any(), anyString(), anyBoolean());
    }
}
