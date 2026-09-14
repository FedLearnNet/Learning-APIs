package de.unihamburg.daibetes.api.workflow.export;


import bio.cosy.feddb.core.api.workflow.node.WorkflowPositionDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.unihamburg.daibetes.api.app.config.FederatedAppConfigNameMapper;
import de.unihamburg.daibetes.api.app.config.input.FederatedAppInputConfigEntity;
import de.unihamburg.daibetes.api.app.config.output.FederatedAppOutputConfigEntity;
import de.unihamburg.daibetes.api.app.version.FederatedAppVersionEntity;
import de.unihamburg.daibetes.api.runs.base.HyperParamMapper;
import de.unihamburg.daibetes.api.workflow.WorkflowEntity;
import de.unihamburg.daibetes.api.workflow.connection.WorkflowConnectionEntity;
import de.unihamburg.daibetes.api.workflow.node.WorkflowNodeEntity;
import de.unihamburg.daibetes.helper.QuarkusMappingConfig;
import org.mapstruct.*;
import org.mapstruct.factory.Mappers;

import java.io.IOException;
import java.util.List;
import java.util.Set;

@Mapper(config = QuarkusMappingConfig.class)
public interface WorkflowExportMapper {

    HyperParamMapper hyperParamMapper = Mappers.getMapper(HyperParamMapper.class);
    ObjectMapper objectMapper = new ObjectMapper();
    FederatedAppConfigNameMapper nameMapper = Mappers.getMapper(FederatedAppConfigNameMapper.class);

    @Mappings({
            @Mapping(target = "nodes", expression = "java(toWorkflowNodeExportDTOs(entity.getNodes()))"),
            @Mapping(target = "connections", expression = "java(toWorkflowConnectionExportDTOs(entity.getConnections()))"),
    })
    WorkflowExportDTO entityToDto(WorkflowEntity entity);

    @Mappings({
            @Mapping(target = "nodes", ignore = true),
            @Mapping(target = "connections", ignore = true),
            @Mapping(target = "createdAt", ignore = true),
            @Mapping(target = "id", ignore = true),
            @Mapping(target = "updatedAt", ignore = true),
            @Mapping(target = "version", ignore = true)
    })
    WorkflowEntity dtoToEntity(WorkflowExportDTO sourceCode);

    @Mappings({
            @Mapping(target = "hyperParams", expression = "java(hyperParamMapper.jsonStringToHyperparams(workflowNodeEntity.getHyperParams()))"),
            @Mapping(target = "position", expression = "java(jsonStringToPosition(workflowNodeEntity.getPosition()))"),
            @Mapping(target = "appUniqueId", ignore = true),
            @Mapping(target = "hasChildren", ignore = true),
            @Mapping(target = "hasParent", ignore = true),
            @Mapping(target = "modelSubPublishHash", ignore = true),
            @Mapping(target = "versionPublishHash", ignore = true)
    })
    WorkflowNodeExportDTO toWorkflowNodeExportDTO(WorkflowNodeEntity workflowNodeEntity);

    @Mappings({
            @Mapping(target = "inputConfigName", source = "inputConfig.name"),
            @Mapping(target = "inputNodeId", source = "inputNode.nodeId"),
            @Mapping(target = "outputConfigName", source = "outputConfig.name"),
            @Mapping(target = "outputNodeId", source = "outputNode.nodeId"),
            @Mapping(target = "inputFileName", source = "inputConfig", qualifiedByName = "mapFileName"),
            @Mapping(target = "outputFileName", source = "outputConfig", qualifiedByName = "mapFileName")
    })
    WorkflowConnectionExportDTO toWorkflowConnectionExportDTO(WorkflowConnectionEntity entity);


    List<WorkflowNodeExportDTO> toWorkflowNodeExportDTOs(Set<WorkflowNodeEntity> entity);

    List<WorkflowConnectionExportDTO> toWorkflowConnectionExportDTOs(Set<WorkflowConnectionEntity> entity);

    @AfterMapping
    default void afterMapping(WorkflowNodeEntity entity, @MappingTarget WorkflowNodeExportDTO dto) {
        if (entity.getSubModel() != null) {
            dto.setVersionPublishHash(entity.getSubModel().getPublishHash());
            FederatedAppVersionEntity appVersion = entity.getSubModel().getModelVersion().getModel().getFederatedAppVersion();

            dto.setAppUniqueId(appVersion.getFederatedApp().getUniqueAppId());
            dto.setVersionPublishHash(appVersion.getPublishHash());
        }
        if (entity.getFederatedAppVersion() != null) {
            dto.setAppUniqueId(entity.getFederatedAppVersion().getFederatedApp().getUniqueAppId());
            dto.setVersionPublishHash(entity.getFederatedAppVersion().getPublishHash());
        }
        if (entity.getInComingConnections() != null) {
            dto.setHasParent(!entity.getInComingConnections().isEmpty());
        }
        if (entity.getOutComingConnections() != null) {
            dto.setHasChildren(!entity.getOutComingConnections().isEmpty());
        }
    }

    default WorkflowPositionDTO jsonStringToPosition(String jsonString) {
        if (jsonString == null || jsonString.isEmpty()) {
            return new WorkflowPositionDTO(0, 0);
        }
        try {
            return objectMapper.readValue(jsonString, WorkflowPositionDTO.class);
        } catch (IOException e) {
            return new WorkflowPositionDTO(0, 0);
        }
    }

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
