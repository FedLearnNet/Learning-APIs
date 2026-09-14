package bio.cosy.feddb.local.api.workflow.node;

import bio.cosy.feddb.core.api.workflow.WorkflowDTO;
import bio.cosy.feddb.core.api.workflow.base.BaseWorkflowEngine;
import bio.cosy.feddb.core.api.workflow.node.WorkflowNodeDTO;
import bio.cosy.feddb.core.api.workflow.node.WorkflowNodeDetailDTO;
import bio.cosy.feddb.core.base.BaseBo;
import bio.cosy.feddb.local.services.GlobalAPIStoreService;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@ApplicationScoped
public class WorkflowNodeBO extends BaseBo<WorkflowNodeDTO, WorkflowNodeEntity, WorkflowNodeAO, WorkflowNodeMapper> {

    @Inject
    @RestClient
    GlobalAPIStoreService globalApi;

    private final BaseWorkflowEngine workflowEngine = new BaseWorkflowEngine();

    public WorkflowNodeDetailDTO getDetailById(Long id) {
        WorkflowNodeEntity entity = ao.findById(id);
        if (entity == null) {
            Log.errorf("Workflow node not found for workflow ID: %d", id);
            throw new IllegalArgumentException("Workflow node not found for workflow ID: " + id);
        }
        return toDetailDto(entity);
    }

    public List<WorkflowNodeDetailDTO> toDetailDto(Set<WorkflowNodeEntity> entities) {
        if (entities == null) return List.of();
        return entities.stream()
                .map(this::toDetailDto)
                .toList();
    }


    public WorkflowNodeDetailDTO toDetailDto(WorkflowNodeEntity entity) {
        WorkflowNodeDTO dto = mapper.entityToDto(entity);
        return toDetailDto(dto);
    }

    public WorkflowNodeDetailDTO toDetailDto(WorkflowNodeDTO dto) {
        WorkflowNodeDetailDTO detailDto = mapper.dtoToDetailDto(dto);
        if (detailDto.getFederatedAppVersionId() != null) {
            detailDto.setAppDetail(globalApi.getAppByVersion(detailDto.getFederatedAppVersionId()));
        }
        if (detailDto.getModelSubId() != null) {
            detailDto.setModelDetail(globalApi.getModelBySub(detailDto.getModelSubId()));
            detailDto.setAppDetail(detailDto.getModelDetail().getFederatedApp());
        }
        dto.setImageName(detailDto.getImageName());
        dto.setOldFCVersion(detailDto.getOldFCVersion());
        dto.setSupportsFederatedLearning(((WorkflowNodeDTO) detailDto).getSupportsFederatedLearning());
        dto.setAppVersion(detailDto.getAppVersion());
        return detailDto;
    }

    public WorkflowNodeEntity createEntity(Long workflowId, WorkflowNodeDetailDTO dto) {
        dto.setWorkflowId(workflowId);
        WorkflowNodeEntity entity = mapper.dtoToEntity(dto);
        entity.setOldFCVersion(dto.getOldFCVersion());
        entity.setSupportsFederatedLearning(((WorkflowNodeDTO) dto).getSupportsFederatedLearning());
        ao.persist(entity);
        return entity;
    }

    public Set<WorkflowNodeEntity> create(Long workflowId, List<WorkflowNodeDetailDTO> nodes) {
        return nodes.stream().map(n -> createEntity(workflowId, n)).collect(Collectors.toSet());
    }


    public WorkflowNodeEntity updateEntity(WorkflowNodeDetailDTO dto) {
        return super.updateEntity(dto);
    }

    public WorkflowNodeEntity createOrUpdate(Long workflowId, WorkflowNodeDetailDTO dto) {
        if (dto.getId() == null) {
            return createEntity(workflowId, dto);
        } else {
            return updateEntity(dto);
        }
    }

    public Set<WorkflowNodeEntity> createOrUpdate(WorkflowDTO dto) {
        if (dto.getId() == null) {
            return Set.of();
        }
        workflowEngine.setExecutionOrder(dto);
        Set<WorkflowNodeEntity> nodes = dto.getNodes().stream()
                .map(n -> createOrUpdate(dto.getId(), n))
                .collect(Collectors.toSet());
        List<Long> nodeIds = nodes.stream().map(WorkflowNodeEntity::getId).toList();
        ao.deleteByWorkflowAndNegativeIds(dto.getId(), nodeIds);
        ao.flush();
        return nodes;
    }

    public Set<WorkflowNodeEntity> createFoRequest(WorkflowDTO dto) {
        if (dto.getId() == null) {
            return Set.of();
        }
        Set<WorkflowNodeEntity> nodes = dto.getNodes().stream()
                .peek(this::setBaseValuesNull)
                .map(n -> createOrUpdate(dto.getId(), n))
                .collect(Collectors.toSet());
        List<Long> nodeIds = nodes.stream().map(WorkflowNodeEntity::getId).toList();
        ao.deleteByWorkflowAndNegativeIds(dto.getId(), nodeIds);
        ao.flush();
        return nodes;
    }

}
