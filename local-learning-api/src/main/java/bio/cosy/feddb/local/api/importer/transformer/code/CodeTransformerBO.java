package bio.cosy.feddb.local.api.importer.transformer.code;

import bio.cosy.feddb.core.api.pipeline.PipelineStatus;
import bio.cosy.feddb.core.base.BaseBo;
import bio.cosy.feddb.local.api.importer.transformer.ConnectorTransformerAO;
import bio.cosy.feddb.local.api.pipeline.PipelineBO;
import bio.cosy.feddb.local.api.pipeline.PipelineDTO;
import bio.cosy.feddb.local.api.pipeline.PipelineEntity;
import bio.cosy.feddb.local.config.FLNetClientConfig;
import bio.cosy.feddb.local.services.orch.OrchDockerServiceClient;
import io.quarkus.logging.Log;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.panache.common.Sort;
import io.smallrye.mutiny.Multi;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ClientErrorException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.Response;
import org.apache.commons.codec.digest.DigestUtils;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.util.List;
import java.util.TreeMap;
import java.util.stream.Collectors;

@ApplicationScoped
public class CodeTransformerBO extends BaseBo<CodeTransformerDTO, CodeTransformerEntity, CodeTransformerAO, CodeTransformerMapper> {

    private static final String IMAGE_PREFIX = "fl-net/code/transformer-";

    @Inject
    CodeTransformerRunnerBO runnerBO;

    @Inject
    PipelineBO pipelineBO;

    @Inject
    ConnectorTransformerAO connectorTransformerAO;

    @Inject
    FLNetClientConfig config;

    @Inject
    @RestClient
    OrchDockerServiceClient dockerClient;

    public static String repository(CodeTransformerEntity entity) {
        return IMAGE_PREFIX + entity.getId();
    }

    public List<CodeTransformerDTO> list() {
        return mapper.entitiesToDtos(ao.listAll(Sort.descending("updatedAt")));
    }

    public CodeTransformerDTO save(CodeTransformerSaveDTO request, String keycloakId) {
        request.setBaseImage(config.connector().codeTransformerImage());
        CodeTransformerSaveResultDTO generated = runnerBO.generate(request, keycloakId);
        if (!Boolean.TRUE.equals(generated.getIsSuccess())) {
            throw new BadRequestException(generated.getMessage());
        }
        CodeTransformerDTO saved = getById(QuarkusTransaction.requiringNew().call(() -> store(request, generated)));
        return saved.getStatus() == CodeTransformerStatus.READY ? saved : build(saved.getId());
    }

    public CodeTransformerDTO build(Long id) {
        pipelineBO.start(id);
        return getById(id);
    }

    public Multi<PipelineDTO> buildUpdates(Long id) {
        PipelineDTO current = getById(id).getPipeline();
        Multi<PipelineDTO> updates = pipelineBO.updatesOf(id);
        return current == null ? updates : Multi.createBy().concatenating().streams(Multi.createFrom().item(current), updates);
    }

    @Transactional
    public void delete(Long id) {
        CodeTransformerEntity entity = find(id);
        List<CodeTransformerUsageDTO> usages = mapper.usages(entity.getImageName());
        if (!usages.isEmpty()) {
            throw new ClientErrorException("Still used by " + usages.stream().map(CodeTransformerUsageDTO::getConnectorName)
                    .collect(Collectors.joining(", ")), Response.Status.CONFLICT);
        }
        ao.delete(entity);
        removeImage(entity.getImageName());
    }

    public void finished(PipelineEntity pipeline) {
        CodeTransformerEntity entity = pipeline.getCodeTransformer();
        boolean latest = pipeline.getDockerTag().equals(entity.getCodeHash());
        if (pipeline.getPipelineStatus() != PipelineStatus.SUCCESS) {
            entity.setStatus(latest ? CodeTransformerStatus.FAILED : entity.getStatus());
            return;
        }
        String image = repository(entity) + ":" + pipeline.getDockerTag();
        String previous = entity.getImageName();
        entity.setImageName(image);
        entity.setStatus(latest ? CodeTransformerStatus.READY : entity.getStatus());
        if (previous != null && !previous.equals(image)) {
            connectorTransformerAO.replaceAppImage(previous, image);
            removeImage(previous);
        }
    }

    Long store(CodeTransformerSaveDTO request, CodeTransformerSaveResultDTO generated) {
        CodeTransformerEntity entity = request.getId() == null ? new CodeTransformerEntity() : find(request.getId());
        mapper.updateFromSave(request, generated, entity);
        entity.setCodeHash(DigestUtils.sha256Hex(new TreeMap<>(generated.getFiles()).toString()).substring(0, 12));
        boolean built = entity.getImageName() != null && entity.getImageName().equals(repository(entity) + ":" + entity.getCodeHash());
        entity.setStatus(built ? CodeTransformerStatus.READY : CodeTransformerStatus.BUILDING);
        ao.persist(entity);
        return entity.getId();
    }

    private CodeTransformerEntity find(Long id) {
        return ao.findByIdOptional(id).orElseThrow(() -> new NotFoundException("Custom transformer " + id + " not found"));
    }

    private void removeImage(String image) {
        if (image == null) {
            return;
        }
        try {
            dockerClient.removeLocalImage(image);
        } catch (RuntimeException e) {
            Log.warnf("Could not remove the image %s: %s", image, e.getMessage());
        }
    }
}
