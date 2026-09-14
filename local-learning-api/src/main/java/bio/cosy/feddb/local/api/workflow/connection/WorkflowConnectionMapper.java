package bio.cosy.feddb.local.api.workflow.connection;


import bio.cosy.feddb.core.api.workflow.connection.WorkflowConnectionDTO;
import bio.cosy.feddb.core.base.BaseMapper;
import bio.cosy.feddb.local.helper.QuarkusMappingConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;

@Mapper(config = QuarkusMappingConfig.class)
public interface WorkflowConnectionMapper extends BaseMapper<WorkflowConnectionDTO, WorkflowConnectionEntity> {

    @Mappings({
            @Mapping(target = "workflowId", source = "workflow.id"),
            @Mapping(target = "inputNodeId", source = "inputNode.nodeId"),
            @Mapping(target = "outputNodeId", source = "outputNode.nodeId")
    })
    WorkflowConnectionDTO entityToDto(WorkflowConnectionEntity entity);

    @Mappings({
            @Mapping(target = "workflow.id", source = "workflowId"),
            @Mapping(target = "inputNode", ignore = true),
            @Mapping(target = "outputNode", ignore = true)
    })
    WorkflowConnectionEntity dtoToEntity(WorkflowConnectionDTO sourceCode);

}
