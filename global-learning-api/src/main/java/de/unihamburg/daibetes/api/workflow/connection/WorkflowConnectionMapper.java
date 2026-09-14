package de.unihamburg.daibetes.api.workflow.connection;


import bio.cosy.feddb.core.api.workflow.connection.WorkflowConnectionDTO;
import bio.cosy.feddb.core.base.BaseMapper;
import de.unihamburg.daibetes.api.app.config.FederatedAppConfigNameMapper;
import de.unihamburg.daibetes.api.app.config.input.FederatedAppInputConfigEntity;
import de.unihamburg.daibetes.api.app.config.output.FederatedAppOutputConfigEntity;
import de.unihamburg.daibetes.api.workflow.export.WorkflowConnectionExportDTO;
import de.unihamburg.daibetes.helper.QuarkusMappingConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;
import org.mapstruct.Named;
import org.mapstruct.factory.Mappers;

@Mapper(config = QuarkusMappingConfig.class)
public interface WorkflowConnectionMapper extends BaseMapper<WorkflowConnectionDTO, WorkflowConnectionEntity> {

    FederatedAppConfigNameMapper nameMapper = Mappers.getMapper(FederatedAppConfigNameMapper.class);

    @Mappings({
            @Mapping(target = "workflowId", source = "workflow.id"),
            @Mapping(target = "inputConfigName", source = "inputConfig.name"),
            @Mapping(target = "inputNodeId", source = "inputNode.nodeId"),
            @Mapping(target = "outputConfigName", source = "outputConfig.name"),
            @Mapping(target = "outputNodeId", source = "outputNode.nodeId"),
            @Mapping(target = "inputFileName", source = "inputConfig", qualifiedByName = "mapFileName"),
            @Mapping(target = "outputFileName", source = "outputConfig", qualifiedByName = "mapFileName")
    })
    WorkflowConnectionDTO entityToDto(WorkflowConnectionEntity entity);

    @Mappings({
            @Mapping(target = "workflow.id", source = "workflowId"),
            @Mapping(target = "inputConfig.id", ignore = true),
            @Mapping(target = "inputNode", ignore = true),
            @Mapping(target = "outputConfig", ignore = true),
            @Mapping(target = "outputNode", ignore = true)
    })
    WorkflowConnectionEntity dtoToEntity(WorkflowConnectionDTO sourceCode);


    @Mappings({
            @Mapping(target = "workflow.id", ignore = true),
            @Mapping(target = "inputConfig.id", ignore = true),
            @Mapping(target = "inputNode", ignore = true),
            @Mapping(target = "outputConfig", ignore = true),
            @Mapping(target = "outputNode", ignore = true),
            @Mapping(target = "createdAt", ignore = true),
            @Mapping(target = "id", ignore = true),
            @Mapping(target = "updatedAt", ignore = true),
            @Mapping(target = "version", ignore = true)
    })
    WorkflowConnectionEntity exportDtoToEntity(WorkflowConnectionExportDTO sourceCode);

    @Named("mapFileName")
    default String mapFileName(FederatedAppInputConfigEntity input) {
        if (input == null) {
            return null;
        }
        String variableName = nameMapper.sanitizeVariableName(input.getName());
        return variableName + "." + input.getType().name().toLowerCase();
    }


    @Named("mapFileName")
    default String mapFileName(FederatedAppOutputConfigEntity output) {
        if (output == null) {
            return null;
        }
        String variableName = nameMapper.sanitizeVariableName(output.getName());
        return variableName + "." + output.getType().name().toLowerCase();
    }


}
