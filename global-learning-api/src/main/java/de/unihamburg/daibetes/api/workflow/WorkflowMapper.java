package de.unihamburg.daibetes.api.workflow;


import bio.cosy.feddb.core.api.workflow.WorkflowDTO;
import bio.cosy.feddb.core.base.BaseMapper;
import de.unihamburg.daibetes.api.workflow.connection.WorkflowConnectionMapper;
import de.unihamburg.daibetes.api.workflow.node.WorkflowNodeMapper;
import de.unihamburg.daibetes.helper.QuarkusMappingConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;
import org.mapstruct.factory.Mappers;

@Mapper(config = QuarkusMappingConfig.class)
public interface WorkflowMapper extends BaseMapper<WorkflowDTO, WorkflowEntity> {

    WorkflowNodeMapper nodeMapper = Mappers.getMapper(WorkflowNodeMapper.class);
    WorkflowConnectionMapper connectionMapper = Mappers.getMapper(WorkflowConnectionMapper.class);


    @Mappings({
            @Mapping(target = "nodes", expression = "java(nodeMapper.entitiesToDtos(entity.getNodes()))"),
            @Mapping(target = "connections", expression = "java(connectionMapper.entitiesToDtos(entity.getConnections()))"),
    })
    WorkflowDTO entityToDto(WorkflowEntity entity);

    @Mappings({
            @Mapping(target = "nodes", ignore = true),
            @Mapping(target = "connections", ignore = true)
    })
    WorkflowEntity dtoToEntity(WorkflowDTO sourceCode);


}
