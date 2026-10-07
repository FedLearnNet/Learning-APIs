package bio.cosy.feddb.local.api.importer.transformer.code.save;

import bio.cosy.feddb.local.api.importer.transformer.ConnectorTransformerAO;
import bio.cosy.feddb.local.api.importer.transformer.code.CodeTransformerEntity;
import bio.cosy.feddb.local.api.importer.transformer.code.CodeTransformerStatus;
import bio.cosy.feddb.local.services.orch.OrchDockerServiceClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class CodeTransformerImageSaveBOTest {

    private static final String OLD = "fl-net/code/transformer-3:old";

    private final CodeTransformerImageSaveBO imageSaveBO = new CodeTransformerImageSaveBO();
    private final CodeTransformerEntity entity = new CodeTransformerEntity();

    @BeforeEach
    void setUp() {
        imageSaveBO.connectorTransformerAO = mock(ConnectorTransformerAO.class);
        imageSaveBO.dockerClient = mock(OrchDockerServiceClient.class);
        entity.setId(3L);
        entity.setImageName(OLD);
        entity.setStatus(CodeTransformerStatus.BUILDING);
    }

    @Test
    void newImageReplacesTheOldOneEverywhere() {
        imageSaveBO.saveImage(entity, "new", true);

        assertEquals("fl-net/code/transformer-3:new", entity.getImageName());
        assertEquals(CodeTransformerStatus.READY, entity.getStatus());
        verify(imageSaveBO.connectorTransformerAO).replaceAppImage(OLD, "fl-net/code/transformer-3:new");
        verify(imageSaveBO.dockerClient).removeLocalImage(OLD);
    }
}
