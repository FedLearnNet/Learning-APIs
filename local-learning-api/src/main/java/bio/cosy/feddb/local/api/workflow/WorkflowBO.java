package bio.cosy.feddb.local.api.workflow;

import bio.cosy.feddb.core.api.app.PublishStatus;
import bio.cosy.feddb.core.api.workflow.WorkflowCreateDTO;
import bio.cosy.feddb.core.api.workflow.WorkflowDTO;
import bio.cosy.feddb.core.base.BaseBo;
import bio.cosy.feddb.local.api.workflow.connection.WorkflowConnectionBO;
import bio.cosy.feddb.local.api.workflow.node.WorkflowNodeBO;
import bio.cosy.feddb.local.config.FLNetClientConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;

import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class WorkflowBO extends BaseBo<WorkflowDTO, WorkflowEntity, WorkflowAO, WorkflowMapper> {

    @Inject
    WorkflowNodeBO workflowNodeBO;

    @Inject
    WorkflowConnectionBO workflowConnectionBO;

    @Inject
    FLNetClientConfig config;

    public WorkflowDTO getForSystem(Long id) {
        Optional<WorkflowEntity> entityOpt = ao.findByUserIdAndAppId(id, config.user().systemUserName());
        if (entityOpt.isEmpty()) {
            throw new NotFoundException("Workflow not found");
        }
        return entityToDto(entityOpt.get());
    }

    public WorkflowDTO get(Long id, String keycloakId) {
        Optional<WorkflowEntity> entityOpt = ao.findByUserIdAndAppId(id, keycloakId);
        if (entityOpt.isEmpty()) {
            throw new NotFoundException("Workflow not found");
        }
        return entityToDto(entityOpt.get());
    }

    public WorkflowDTO entityToDto(WorkflowEntity entity) {
        WorkflowDTO dto = mapper.entityToDto(entity);
        if (dto.getNodes() == null) {
            dto.setNodes(List.of());
        }else{
            dto.setNodes(workflowNodeBO.toDetailDto(entity.getNodes()));
        }

        return dto;
    }

    private WorkflowEntity createEmptyEntity(String keycloakId) {
        WorkflowEntity entity = new WorkflowEntity();
        entity.setPublishStatus(PublishStatus.UNPUBLISHED);
        entity.setKeycloakId(keycloakId);
        ao.persist(entity);
        return entity;
    }

    public WorkflowDTO create(WorkflowCreateDTO createDTO, String keycloakId) {
        //TODO IF WE HAVE MORE COMPLEX LOGIC LATER
        WorkflowEntity entity = createEmptyEntity(keycloakId);
        ao.persist(entity);
        if (createDTO.getProjectId() != null) {
            throw new UnsupportedOperationException("Linking workflow to project not possible in the clinic");
        }
        return entityToDto(entity);
    }

    public WorkflowEntity createForProject(WorkflowDTO dto) {
        WorkflowEntity entity = new WorkflowEntity();
        entity.setKeycloakId(config.user().systemUserName());
        ao.persist(entity);
        dto.setId(entity.getId());
        entity.setPublishStatus(PublishStatus.PUBLISHED);
        entity.setNodes(workflowNodeBO.createFoRequest(dto));
        entity.setConnections(workflowConnectionBO.createFoRequest(dto, entity.getNodes()));
        entity.setPublishStatus(dto.getPublishStatus());
        return entity;
    }



    public WorkflowDTO update(WorkflowDTO dto, String keycloakId) {
        Optional<WorkflowEntity> entityOpt = ao.findByUserIdAndAppId(dto.getId(), keycloakId);
        WorkflowEntity entity = entityOpt.orElse(createEmptyEntity(keycloakId));
        entity.setNodes(workflowNodeBO.createOrUpdate(dto));
        entity.setConnections(workflowConnectionBO.createOrUpdate(dto, entity.getNodes()));
        entity.setPublishStatus(dto.getPublishStatus());
        return entityToDto(entity);
    }

    public void delete(Long id, String keycloakId) {
        Optional<WorkflowEntity> entityOpt = ao.findByUserIdAndAppId(id, keycloakId);
        entityOpt.ifPresent(workflowEntity -> ao.delete(workflowEntity));
    }
}
