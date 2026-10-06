package bio.cosy.feddb.local.api.importer.transformer.code.save;

import bio.cosy.feddb.local.api.importer.transformer.ConnectorTransformerAO;
import bio.cosy.feddb.local.api.importer.transformer.code.CodeTransformerEntity;
import bio.cosy.feddb.local.api.importer.transformer.code.CodeTransformerStatus;
import bio.cosy.feddb.local.api.pipeline.PipelineBO;
import bio.cosy.feddb.local.api.pipeline.PipelineDTO;
import bio.cosy.feddb.local.services.orch.OrchDockerServiceClient;
import io.quarkus.logging.Log;
import io.smallrye.mutiny.Multi;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.commons.codec.digest.DigestUtils;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.util.TreeMap;

@ApplicationScoped
public class CodeTransformerImageSaveBO {

    private static final String IMAGE_PREFIX = "fl-net/code/transformer-";

    @Inject
    @RestClient
    OrchDockerServiceClient dockerClient;

    @Inject
    ConnectorTransformerAO connectorTransformerAO;

    @Inject
    PipelineBO pipelineBO;

    public static String repository(CodeTransformerEntity entity) {
        return IMAGE_PREFIX + entity.getId();
    }

    public void removeImage(String image) {
        if (image == null) {
            return;
        }
        try {
            dockerClient.removeLocalImage(image);
        } catch (RuntimeException e) {
            Log.warnf("Could not remove the image %s: %s", image, e.getMessage());
        }
    }

    public void saveImage(CodeTransformerEntity entity, String dockerTag, boolean latest) {
        String image = repository(entity) + ":" + dockerTag;
        String previous = entity.getImageName();
        entity.setImageName(image);
        entity.setStatus(latest ? CodeTransformerStatus.READY : entity.getStatus());
        if (previous != null && !previous.equals(image)) {
            connectorTransformerAO.replaceAppImage(previous, image);
            removeImage(previous);
        }
    }

    public void build(Long id) {
        pipelineBO.start(id);
    }

    public Multi<PipelineDTO> buildUpdates(Long id, PipelineDTO current) {
        Multi<PipelineDTO> updates = pipelineBO.updatesOf(id);
        if (current == null) {
            return updates;
        }
        return Multi.createBy().concatenating().streams(Multi.createFrom().item(current), updates);
    }

    public void store(CodeTransformerEntity entity, CodeTransformerSaveDTO request, CodeTransformerSaveResultDTO generated) {
        entity.setName(request.getName());
        entity.setDescription(request.getDescription());
        entity.setMode(request.getMode());
        entity.setBody(request.getBody());
        entity.setGoal(request.getGoal());
        entity.setSourceGoals(request.getSourceGoals());
        entity.setData(request.getData());
        entity.setInputKeys(generated.getInputKeys());
        entity.setReturnKeys(generated.getReturnKeys());
        entity.setFiles(generated.getFiles());
        entity.setCodeHash(DigestUtils.sha256Hex(new TreeMap<>(generated.getFiles()).toString()).substring(0, 12));
        boolean built = entity.getImageName() != null && entity.getImageName().equals(repository(entity) + ":" + entity.getCodeHash());
        entity.setStatus(built ? CodeTransformerStatus.READY : CodeTransformerStatus.BUILDING);
    }
}
