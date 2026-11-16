package bio.cosy.feddb.local.api.importer.transformer.code;

import bio.cosy.feddb.core.api.pipeline.PipelineStatus;
import bio.cosy.feddb.core.base.BaseBo;
import bio.cosy.feddb.local.api.importer.transformer.ConnectorTransformerAO;
import bio.cosy.feddb.local.api.importer.transformer.code.runner.CodeTransformerRequestBO;
import bio.cosy.feddb.local.api.importer.transformer.code.save.CodeTransformerImageSaveBO;
import bio.cosy.feddb.local.api.importer.transformer.code.save.CodeTransformerSaveDTO;
import bio.cosy.feddb.local.api.importer.transformer.code.save.CodeTransformerSaveResultDTO;
import bio.cosy.feddb.local.api.pipeline.PipelineBO;
import bio.cosy.feddb.local.api.pipeline.PipelineDTO;
import bio.cosy.feddb.local.api.pipeline.PipelineEntity;
import bio.cosy.feddb.local.config.FLNetClientConfig;
import io.quarkus.panache.common.Sort;
import io.smallrye.mutiny.Multi;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ClientErrorException;
import jakarta.ws.rs.core.Response;

import java.util.List;
import java.util.stream.Collectors;

@ApplicationScoped
public class CodeTransformerBO extends BaseBo<CodeTransformerDTO, CodeTransformerEntity, CodeTransformerAO, CodeTransformerMapper> {


    @Inject
    CodeTransformerRequestBO requestBO;

    @Inject
    PipelineBO pipelineBO;

    @Inject
    ConnectorTransformerAO connectorTransformerAO;

    @Inject
    FLNetClientConfig config;

    @Inject
    CodeTransformerImageSaveBO transformerImageBO;

    public List<CodeTransformerDTO> list() {
        return mapper.entitiesToDtos(ao.listAll(Sort.descending("updatedAt")));
    }

    public CodeTransformerDTO save(CodeTransformerSaveDTO request, String keycloakId) {
        request.setBaseImage(config.connector().codeTransformerImage());
        CodeTransformerSaveResultDTO generated = requestBO.generate(request, keycloakId);
        if (!Boolean.TRUE.equals(generated.getIsSuccess())) {
            throw new BadRequestException(generated.getMessage());
        }
        CodeTransformerDTO saved = store(request, generated);
        if (saved.getStatus() == CodeTransformerStatus.READY) {
            return saved;
        }
        return build(saved.getId());
    }

    @Transactional
    public CodeTransformerDTO store(CodeTransformerSaveDTO request, CodeTransformerSaveResultDTO generated) {
        CodeTransformerEntity entity = request.getId() == null ? new CodeTransformerEntity() : ao.findByIdChecked(request.getId());
        transformerImageBO.store(entity, request, generated);
        ao.persist(entity);
        return mapper.entityToDto(entity);
    }

    public CodeTransformerDTO build(Long id) {
        pipelineBO.start(id);
        return getById(id);
    }

    public Multi<PipelineDTO> buildUpdates(Long id) {
        PipelineDTO current = getById(id).getPipeline();
        return transformerImageBO.buildUpdates(id, current);
    }

    @Transactional
    public void delete(Long id) {
        CodeTransformerEntity entity = ao.findByIdChecked(id);
        List<CodeTransformerUsageDTO> usages = mapper.usages(entity.getImageName());
        if (!usages.isEmpty()) {
            throw new ClientErrorException("Still used by " + usages.stream().map(CodeTransformerUsageDTO::getConnectorName)
                    .collect(Collectors.joining(", ")), Response.Status.CONFLICT);
        }
        ao.delete(entity);
        transformerImageBO.removeImage(entity.getImageName());
    }

    public void finished(PipelineEntity pipeline) {
        CodeTransformerEntity entity = pipeline.getCodeTransformer();
        boolean latest = pipeline.getDockerTag().equals(entity.getCodeHash());
        if (pipeline.getPipelineStatus() != PipelineStatus.SUCCESS && latest) {
            entity.setStatus(CodeTransformerStatus.FAILED);
            return;
        }
        transformerImageBO.saveImage(entity, pipeline.getDockerTag(), latest);
    }

}
