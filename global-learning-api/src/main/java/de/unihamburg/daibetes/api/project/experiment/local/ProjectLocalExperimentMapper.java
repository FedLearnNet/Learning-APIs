package de.unihamburg.daibetes.api.project.experiment.local;

import bio.cosy.feddb.core.base.BaseMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.unihamburg.daibetes.api.project.experiment.local.step.ProjectLocalExperimentStepEntity;
import de.unihamburg.daibetes.api.project.experiment.local.step.ProjectLocalExperimentStepMapper;
import de.unihamburg.daibetes.api.runs.experiment.ExperimentDiagramConfigDTO;
import de.unihamburg.daibetes.helper.QuarkusMappingConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;
import org.mapstruct.Named;
import org.mapstruct.factory.Mappers;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;


@Mapper(config = QuarkusMappingConfig.class)
public interface ProjectLocalExperimentMapper extends BaseMapper<ProjectLocalExperimentDTO, ProjectLocalExperimentEntity> {
    ObjectMapper objectMapper = new ObjectMapper();

    ProjectLocalExperimentStepMapper stepMapper = Mappers.getMapper(ProjectLocalExperimentStepMapper.class);

    @Mappings({
            @Mapping(target = "diagramConfigs", expression = "java(jsonStringToDiagramConfig(entity.getDiagramConfig()))"),
            @Mapping(target = "projectId", source = "project.id"),
            @Mapping(target = "currentWorkflowNodeId", source = "currentWorkflowNode.id"),
            @Mapping(target = "steps", expression = "java(stepMapper.entitiesToDtos(entity.getSteps()))"),
            @Mapping(target = "workflowId", source = "project.workflow.id"),
            @Mapping(target = "workflowVersion", source = "project.workflow.version")
    })
    ProjectLocalExperimentDTO entityToDto(ProjectLocalExperimentEntity entity);


    @Mappings({
            @Mapping(target = "diagramConfig", expression = "java(diagramConfigToJsonString(dto.getDiagramConfigs()))"),
            @Mapping(target = "project.id", source = "projectId"),
            @Mapping(target = "currentWorkflowNode", source = "currentWorkflowNodeId", qualifiedByName = "mapCurrentStep"),
            @Mapping(target = "steps", ignore = true),
    })
    ProjectLocalExperimentEntity dtoToEntity(ProjectLocalExperimentDTO dto);

    @Mappings({
            @Mapping(target = "createdAt", ignore = true),
            @Mapping(target = "diagramConfigs", ignore = true),
            @Mapping(target = "finishedAt", ignore = true),
            @Mapping(target = "id", ignore = true),
            @Mapping(target = "projectId", ignore = true),
            @Mapping(target = "startedAt", ignore = true),
            @Mapping(target = "updatedAt", ignore = true),
            @Mapping(target = "version", ignore = true),
            @Mapping(target = "currentWorkflowNodeId", ignore = true),
            @Mapping(target = "experimentStatus", ignore = true),
            @Mapping(target = "groupId", ignore = true),
            @Mapping(target = "steps", ignore = true),
            @Mapping(target = "testRun", ignore = true),
            @Mapping(target = "workflowId", ignore = true),
            @Mapping(target = "workflowVersion", ignore = true)
    })
    ProjectLocalExperimentDTO createDtoToDto(CreateProjectLocalExperimentDTO dto);


    @Named("mapCurrentStep")
    default ProjectLocalExperimentStepEntity mapCurrentStep(Long currentWorkflowNodeId) {
        if (currentWorkflowNodeId == null) {
            return null;
        }
        ProjectLocalExperimentStepEntity stepEntity = new ProjectLocalExperimentStepEntity();
        stepEntity.setId(currentWorkflowNodeId);
        return stepEntity;
    }

    default List<ExperimentDiagramConfigDTO> jsonStringToDiagramConfig(String jsonString) {
        if (jsonString == null || jsonString.isEmpty()) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(jsonString,
                    objectMapper.getTypeFactory().constructCollectionType(List.class,
                            ExperimentDiagramConfigDTO.class));
        } catch (IOException e) {
            throw new RuntimeException("Error parsing JSON string to LinkedHashMap", e);
        }
    }

    default String diagramConfigToJsonString(List<ExperimentDiagramConfigDTO> diagramConfigs) {
        if (diagramConfigs == null || diagramConfigs.isEmpty()) {
            return "";
        }
        try {
            return objectMapper.writeValueAsString(diagramConfigs);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Error converting ExperimentDiagramConfigDTO to JSON string", e);
        }
    }


}
