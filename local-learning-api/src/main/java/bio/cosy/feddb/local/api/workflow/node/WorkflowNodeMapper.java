package bio.cosy.feddb.local.api.workflow.node;


import bio.cosy.feddb.core.api.workflow.node.WorkflowNodeDTO;
import bio.cosy.feddb.core.api.workflow.node.WorkflowNodeDetailDTO;
import bio.cosy.feddb.core.api.workflow.node.WorkflowPositionDTO;
import bio.cosy.feddb.core.base.BaseMapper;
import bio.cosy.feddb.local.helper.QuarkusMappingConfig;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.mapstruct.*;
import org.mapstruct.factory.Mappers;

import java.io.IOException;

@Mapper(config = QuarkusMappingConfig.class)
public interface WorkflowNodeMapper extends BaseMapper<WorkflowNodeDTO, WorkflowNodeEntity> {

    HyperParamMapper hyperParamMapper = Mappers.getMapper(HyperParamMapper.class);
    ObjectMapper objectMapper = new ObjectMapper();

    @Mappings({
            @Mapping(target = "workflow.id", source = "workflowId"),
            @Mapping(target = "hyperParams", expression = "java(hyperParamMapper.hyperparamsToJsonString(dto.getHyperParams()))"),
            @Mapping(target = "position", expression = "java(positionToJsonString(dto.getPosition()))"),
            @Mapping(target = "inComingConnections", ignore = true),
            @Mapping(target = "outComingConnections", ignore = true),
    })
    WorkflowNodeEntity dtoToEntity(WorkflowNodeDTO dto);


    @Mappings({
            @Mapping(target = "workflowId", source = "workflow.id"),
            @Mapping(target = "appVersion", ignore = true),  //IN BO
            @Mapping(target = "hasChildren", ignore = true),
            @Mapping(target = "hasParent", ignore = true),
            @Mapping(target = "imageName", ignore = true),  //IN BO
            @Mapping(target = "oldFCVersion", ignore = true), //IN BO
            @Mapping(target = "hyperParams", expression = "java(hyperParamMapper.jsonStringToHyperparams(entity.getHyperParams()))"),
            @Mapping(target = "position", expression = "java(jsonStringToPosition(entity.getPosition()))"),
    })
    WorkflowNodeDTO entityToDto(WorkflowNodeEntity entity);


    @Mappings({
            @Mapping(target = "appDetail", ignore = true),
            @Mapping(target = "modelDetail", ignore = true),
            @Mapping(target = "appInputConfig", ignore = true)
    })
    WorkflowNodeDetailDTO dtoToDetailDto(WorkflowNodeDTO dto);

    @AfterMapping
    default void afterDetailMapping(WorkflowNodeEntity entity, @MappingTarget WorkflowNodeDetailDTO dto) {
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


    default String positionToJsonString(WorkflowPositionDTO position) {
        if (position == null) {
            return "";
        }
        try {
            return objectMapper.writeValueAsString(position);
        } catch (JsonProcessingException e) {
            return "";
        }
    }

}
