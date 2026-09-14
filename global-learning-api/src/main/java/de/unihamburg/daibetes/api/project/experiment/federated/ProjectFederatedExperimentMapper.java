package de.unihamburg.daibetes.api.project.experiment.federated;

import bio.cosy.feddb.core.api.project.ProjectDetailDTO;
import bio.cosy.feddb.core.api.socket.ProjectFederatedExperimentForLocalDTO;
import bio.cosy.feddb.core.api.workflow.WorkflowDTO;
import bio.cosy.feddb.core.base.BaseMapper;
import bio.cosy.feddb.core.api.project.ProjectStatus;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.unihamburg.daibetes.api.project.experiment.federated.participants.ProjectFederatedExperimentParticipantEntity;
import de.unihamburg.daibetes.api.project.experiment.federated.participants.ProjectFederatedExperimentParticipantMapper;
import de.unihamburg.daibetes.api.project.experiment.federated.step.ProjectFederatedExperimentStepEntity;
import de.unihamburg.daibetes.api.project.experiment.federated.step.ProjectFederatedExperimentStepMapper;
import de.unihamburg.daibetes.api.runs.experiment.ExperimentDiagramConfigDTO;
import de.unihamburg.daibetes.helper.QuarkusMappingConfig;
import org.mapstruct.*;
import org.mapstruct.factory.Mappers;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;


@Mapper(config = QuarkusMappingConfig.class)
public interface ProjectFederatedExperimentMapper extends BaseMapper<ProjectFederatedExperimentDTO, ProjectFederatedExperimentEntity> {
    ProjectFederatedExperimentParticipantMapper participantMapper = Mappers.getMapper(ProjectFederatedExperimentParticipantMapper.class);
    ProjectFederatedExperimentStepMapper stepMapper = Mappers.getMapper(ProjectFederatedExperimentStepMapper.class);

    ObjectMapper objectMapper = new ObjectMapper();

    @Mappings({
            @Mapping(target = "diagramConfigs", expression = "java(jsonStringToDiagramConfig(entity.getDiagramConfig()))"),
            @Mapping(target = "projectVersion", expression = "java(jsonStringToProject(entity.getProjectVersion()))"),
            @Mapping(target = "projectId", source = "project.id"),
            @Mapping(target = "coordinatorId", source = "coordinator.id", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.SET_TO_NULL),
            @Mapping(target = "currentWorkflowNodeId", source = "currentWorkflowNode.id"),
            @Mapping(target = "groupId", ignore = true),
            @Mapping(target = "workflowId", source = "workflow.id")
    })
    ProjectFederatedExperimentDTO entityToDto(ProjectFederatedExperimentEntity entity);

    @Mappings({
            @Mapping(target = "diagramConfigs", expression = "java(jsonStringToDiagramConfig(entity.getDiagramConfig()))"),
            @Mapping(target = "projectVersion", expression = "java(jsonStringToProject(entity.getProjectVersion()))"),
            @Mapping(target = "projectId", source = "project.id"),
            @Mapping(target = "coordinatorId", source = "coordinator.id", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.SET_TO_NULL),
            @Mapping(target = "participants", expression = "java(participantMapper.entitiesToDtos(entity.getParticipants()))"),
            @Mapping(target = "steps", expression = "java(stepMapper.entitiesToDtos(entity.getSteps()))"),
            @Mapping(target = "currentWorkflowNodeId", source = "entity.currentWorkflowNode.id"),
            @Mapping(target = "groupId", ignore = true),
            @Mapping(target = "workflowId", source = "workflow.id")
    })
    ProjectFederatedExperimentDetailDTO entityToDetailDto(ProjectFederatedExperimentEntity entity);


    @Mappings({
            @Mapping(target = "keycloakId", source = "keycloakId"),
            @Mapping(target = "roles", source = "roles"),
            @Mapping(target = "workflow", source = "workflow"),
            @Mapping(target = "id", source = "dto.id"),
            @Mapping(target = "createdAt", source = "dto.createdAt"),
            @Mapping(target = "updatedAt", source = "dto.updatedAt"),
            @Mapping(target = "version", source = "dto.version"),
            @Mapping(target = "name", source = "dto.name"),
            @Mapping(target = "description", source = "dto.description"),
            @Mapping(target = "modelNeedToBePublic", source = "dto.modelNeedToBePublic"),
    })
    ProjectFederatedExperimentForLocalDTO dtoToSimpleDTO(ProjectFederatedExperimentDTO dto,
                                                         String keycloakId,
                                                         Set<String> roles,
                                                         WorkflowDTO workflow);


    @Mappings({
            @Mapping(target = "projectVersion", expression = "java(projectToJsonString(dto.getProjectVersion()))"),
            @Mapping(target = "diagramConfig", expression = "java(diagramConfigToJsonString(dto.getDiagramConfigs()))"),
            @Mapping(target = "project.id", source = "projectId"),
            @Mapping(target = "coordinator", source = "coordinatorId", qualifiedByName = "coordinatorItToCoordinator"),
            @Mapping(target = "participants", ignore = true),
            @Mapping(target = "steps", ignore = true),
            @Mapping(target = "currentWorkflowNode", source = "currentWorkflowNodeId", qualifiedByName = "mapCurrentStep"),
            @Mapping(target = "workflow.id", source = "workflowId")
    })
    ProjectFederatedExperimentEntity dtoToEntity(ProjectFederatedExperimentDTO dto);

    @Mappings({
            @Mapping(target = "acceptanceCount", constant = "0L"),
            @Mapping(target = "createdAt", ignore = true),
            @Mapping(target = "diagramConfigs", ignore = true),
            @Mapping(target = "finishedAt", ignore = true),
            @Mapping(target = "id", ignore = true),
            @Mapping(target = "projectId", source = "projectId"),
            @Mapping(target = "projectVersion", ignore = true),
            @Mapping(target = "relayServerAddress", source = "relayServerAddress"),
            @Mapping(target = "startedAt", ignore = true),
            @Mapping(target = "updatedAt", ignore = true),
            @Mapping(target = "version", ignore = true),
            @Mapping(target = "acceptanceClinicCount", ignore = true),
            @Mapping(target = "clinicIsCoordinator", ignore = true),
            @Mapping(target = "coordinatorId", ignore = true),
            @Mapping(target = "channelId", source = "channelId"),
            @Mapping(target = "currentWorkflowNodeId", ignore = true),
            @Mapping(target = "experimentStatus", expression = "java(getProjectInitStatus())"),
            @Mapping(target = "groupId", ignore = true),
            @Mapping(target = "workflowId", ignore = true),
            @Mapping(target = "globalUniqueId", ignore = true),
            @Mapping(target = "modelCanBePublic", constant = "true")
    })
    ProjectFederatedExperimentDTO createDtoToDto(CreateProjectFederatedExperimentDTO dto,
                                                 Long projectId,
                                                 String channelId,
                                                 String relayServerAddress);

    @Named("coordinatorItToCoordinator")
    default ProjectFederatedExperimentParticipantEntity coordinatorItToCoordinator(Long CoordinatorId) {
        if (CoordinatorId == null) {
            return null;
        }
        ProjectFederatedExperimentParticipantEntity e = new ProjectFederatedExperimentParticipantEntity();
        e.setId(CoordinatorId);
        return e;
    }

    @Named("mapCurrentStep")
    default ProjectFederatedExperimentStepEntity mapCurrentStep(Long currentWorkflowNodeId) {
        if (currentWorkflowNodeId == null) {
            return null;
        }
        ProjectFederatedExperimentStepEntity stepEntity = new ProjectFederatedExperimentStepEntity();
        stepEntity.setId(currentWorkflowNodeId);
        return stepEntity;
    }

    default ProjectStatus getProjectInitStatus() {
        return ProjectStatus.INIT;
    }

    default ProjectDetailDTO jsonStringToProject(String jsonString) {
        if (jsonString == null || jsonString.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.readValue(jsonString,
                    objectMapper.getTypeFactory().constructType(ProjectDetailDTO.class));
        } catch (IOException e) {
            throw new RuntimeException("Error parsing JSON string to LinkedHashMap", e);
        }
    }

    default WorkflowDTO jsonStringToWorkflow(String jsonString) {
        if (jsonString == null || jsonString.isEmpty()) {
            return null;
        }
        try {
            return objectMapper
                    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                    .readValue(jsonString,
                            objectMapper.getTypeFactory().constructType(WorkflowDTO.class));
        } catch (IOException e) {
            throw new RuntimeException("Error parsing JSON string to LinkedHashMap", e);
        }
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

    default String projectToJsonString(ProjectDetailDTO project) {
        if (project == null) {
            return "";
        }
        try {
            return objectMapper.writeValueAsString(project);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Error converting project to JSON string", e);
        }
    }

    default String workflowToJsonString(WorkflowDTO workflow) {
        if (workflow == null) {
            return "";
        }
        try {
            return objectMapper.writeValueAsString(workflow);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Error converting workflow to JSON string", e);
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
