package bio.cosy.feddb.local.api.workflow;


import bio.cosy.feddb.core.api.workflow.WorkflowDTO;
import bio.cosy.feddb.core.base.BaseMapper;
import bio.cosy.feddb.local.api.workflow.connection.WorkflowConnectionMapper;
import bio.cosy.feddb.local.helper.QuarkusMappingConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;
import org.mapstruct.factory.Mappers;

@Mapper(config = QuarkusMappingConfig.class)
public interface WorkflowMapper extends BaseMapper<WorkflowDTO, WorkflowEntity> {

    WorkflowConnectionMapper connectionMapper = Mappers.getMapper(WorkflowConnectionMapper.class);


    @Mappings({
            @Mapping(target = "nodes", ignore = true), //will be later in bo
            @Mapping(target = "connections", expression = "java(connectionMapper.entitiesToDtos(entity.getConnections()))"),
    })
    WorkflowDTO entityToDto(WorkflowEntity entity);

    @Mappings({
            @Mapping(target = "nodes", ignore = true),
            @Mapping(target = "connections", ignore = true)
    })
    WorkflowEntity dtoToEntity(WorkflowDTO sourceCode);


}
