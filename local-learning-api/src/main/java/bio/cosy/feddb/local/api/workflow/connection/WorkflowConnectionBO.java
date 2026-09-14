package bio.cosy.feddb.local.api.workflow.connection;

import bio.cosy.feddb.core.api.workflow.WorkflowDTO;
import bio.cosy.feddb.core.api.workflow.connection.WorkflowConnectionDTO;
import bio.cosy.feddb.core.base.BaseBo;
import bio.cosy.feddb.local.api.workflow.node.WorkflowNodeEntity;
import jakarta.enterprise.context.ApplicationScoped;
import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@ApplicationScoped
public class WorkflowConnectionBO extends BaseBo<WorkflowConnectionDTO, WorkflowConnectionEntity, WorkflowConnectionAO, WorkflowConnectionMapper> {


    public WorkflowConnectionEntity createEntity(Long workflowId, WorkflowConnectionDTO dto, Set<WorkflowNodeEntity> nodes) {
        dto.setWorkflowId(workflowId);
        WorkflowConnectionEntity entity = mapper.dtoToEntity(dto);
        if (StringUtils.isNotEmpty(dto.getInputNodeId())) {
            nodes.stream().filter(n -> n.getNodeId().equals(dto.getInputNodeId())).findFirst()
                    .ifPresent(entity::setInputNode);
        }
        if (StringUtils.isNotEmpty(dto.getOutputNodeId())) {
            nodes.stream().filter(n -> n.getNodeId().equals(dto.getOutputNodeId())).findFirst()
                    .ifPresent(entity::setOutputNode);
        }
        ao.persist(entity);
        return entity;
    }

    public Set<WorkflowConnectionEntity> create(Long workflowId, List<WorkflowConnectionDTO> edges, Set<WorkflowNodeEntity> nodes) {
        return edges.stream().map(e -> createEntity(workflowId, e, nodes)).collect(Collectors.toSet());
    }


    @Override
    public WorkflowConnectionEntity mergeEntity(WorkflowConnectionEntity foundEntity, WorkflowConnectionEntity entity) {
        //TODO IF WE HAVE SOME PROP LATERS
        return foundEntity;
    }

    public WorkflowConnectionEntity updateEntity(WorkflowConnectionDTO dto) {
        return super.updateEntity(dto);
    }

    public WorkflowConnectionEntity createOrUpdate(Long workflowId, WorkflowConnectionDTO dto, Set<WorkflowNodeEntity> nodes) {
        if (dto.getId() == null) {
            return createEntity(workflowId, dto, nodes);
        } else {
            return updateEntity(dto);
        }
    }

    public Set<WorkflowConnectionEntity> createOrUpdate(WorkflowDTO dto, Set<WorkflowNodeEntity> nodes) {
        if (dto.getId() == null || dto.getConnections() == null) {
            return Set.of();
        }
        Set<WorkflowConnectionEntity> edges = dto.getConnections().stream()
                .map(e -> createOrUpdate(dto.getId(), e, nodes))
                .collect(Collectors.toSet());
        List<Long> edgesId = edges.stream().map(WorkflowConnectionEntity::getId).toList();
        ao.deleteByWorkflowAndNegativeIds(dto.getId(), edgesId);
        return edges;
    }
    public Set<WorkflowConnectionEntity> createFoRequest(WorkflowDTO dto, Set<WorkflowNodeEntity> nodes) {
        if (dto.getId() == null || dto.getConnections() == null) {
            return Set.of();
        }
        Set<WorkflowConnectionEntity> edges = dto.getConnections().stream()
                .peek(this::setBaseValuesNull)
                .map(e -> createOrUpdate(dto.getId(), e, nodes))
                .collect(Collectors.toSet());
        List<Long> edgesId = edges.stream().map(WorkflowConnectionEntity::getId).toList();
        ao.deleteByWorkflowAndNegativeIds(dto.getId(), edgesId);
        return edges;
    }



    public WorkflowConnectionEntity filterForFileName(List<WorkflowConnectionEntity> edges, String fileName) {
        if (edges == null || edges.isEmpty() || StringUtils.isEmpty(fileName)) {
            return null;
        }
        return edges.stream()
                .filter(e -> {
                    String outputConfigName = e.getOutputConfigName();
                    boolean isEqName = outputConfigName.equals(fileName);
                    return isEqName;
                })
                .findFirst()
                .orElse(null);
    }
}
