package de.unihamburg.daibetes.api.workflow;

import bio.cosy.feddb.core.api.app.PublishStatus;
import bio.cosy.feddb.core.api.workflow.WorkflowCreateDTO;
import bio.cosy.feddb.core.api.workflow.WorkflowDTO;
import bio.cosy.feddb.core.base.BaseBo;
import de.unihamburg.daibetes.api.app.FederatedAppBO;
import de.unihamburg.daibetes.api.project.ProjectBO;
import de.unihamburg.daibetes.api.workflow.connection.WorkflowConnectionBO;
import de.unihamburg.daibetes.api.workflow.export.WorkflowExportDTO;
import de.unihamburg.daibetes.api.workflow.node.WorkflowNodeBO;
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
    FederatedAppBO federatedAppBO;

    @Inject
    ProjectBO projectBO;

    public List<WorkflowDTO> listWorkflows(String keycloakId) {
        return mapper.entitiesToDtos(ao.listWorkflows(keycloakId));
    }

    public List<WorkflowDTO> listWorkflowForApp(Long appId, String keycloakId) {
        return mapper.entitiesToDtos(ao.listWorkflowForApp(appId, keycloakId));
    }


    public WorkflowDTO get(Long id, String keycloakId) {
        Optional<WorkflowEntity> entityOpt = ao.findByUserIdAndWorkflowId(id, keycloakId);
        if (entityOpt.isEmpty()) {
            throw new NotFoundException("Workflow not found");
        }
        return entityToDto(entityOpt.get());
    }

    public WorkflowDTO entityToDto(WorkflowEntity entity) {
        WorkflowDTO dto = mapper.entityToDto(entity);
        if (dto.getNodes() == null) {
            dto.setNodes(List.of());
        }
        dto.getNodes().forEach(n -> {
            n.setAppDetail(federatedAppBO.dtoToDetail(n.getAppDetail()));
        });
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
        //TODO MORE COMPLEX LOGIC
        WorkflowEntity entity = createEmptyEntity(keycloakId);
        ao.persist(entity);
        if (createDTO.getProjectId() != null) {
            projectBO.setWorkflow(createDTO.getProjectId(), keycloakId, entity);
        }
        return entityToDto(entity);
    }

    public WorkflowDTO createNextEmpty( String keycloakId) {
        Optional<WorkflowEntity> emptyEntity = ao.findEmptyByUserId(keycloakId);
        if (emptyEntity.isPresent()) {
            return entityToDto(emptyEntity.get());
        }
        WorkflowEntity entity = createEmptyEntity(keycloakId);
        ao.persist(entity);
        return entityToDto(entity);
    }



    public WorkflowDTO update(WorkflowDTO dto, String keycloakId) {
        Optional<WorkflowEntity> entityOpt = ao.findByUserIdAndWorkflowId(dto.getId(), keycloakId);
        WorkflowEntity entity = entityOpt.orElseGet(() -> createEmptyEntity(keycloakId));

        entity.setNodes(workflowNodeBO.createOrUpdate(dto));
        entity.setConnections(workflowConnectionBO.createOrUpdate(dto, entity.getNodes()));
        entity.setPublishStatus(dto.getPublishStatus());
        entity.setName(dto.getName());
        entity.setDescription(dto.getDescription());
        entity.setInputs(dto.getInputs());
        ao.persist(entity);
        return entityToDto(entity);
    }

    public void delete(Long id, String keycloakId) {
        Optional<WorkflowEntity> entityOpt = ao.findByUserIdAndWorkflowId(id, keycloakId);
        entityOpt.ifPresent(workflowEntity -> ao.delete(workflowEntity));
    }


    public WorkflowEntity cloneWorkflow(WorkflowDTO dto) {
        WorkflowEntity entity = createEmptyEntity(dto.getKeycloakId());
        ao.persist(entity);
        dto.setId(entity.getId());
        entity.setPublishStatus(PublishStatus.UNPUBLISHED);
        entity.setNodes(workflowNodeBO.clone(dto));
        entity.setConnections(workflowConnectionBO.clone(dto, entity.getNodes()));
        ao.persist(entity);
        return entity;
    }
}
