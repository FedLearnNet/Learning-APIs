package de.unihamburg.daibetes.execution.experiment;

import bio.cosy.feddb.core.api.project.ProjectDetailDTO;
import bio.cosy.feddb.core.api.socket.ProjectFederatedExperimentForLocalDTO;
import bio.cosy.feddb.core.api.workflow.WorkflowDTO;
import de.unihamburg.daibetes.api.app.version.FederatedAppVersionEntity;
import de.unihamburg.daibetes.api.development.run.CreateExperimentDTO;
import de.unihamburg.daibetes.api.project.ProjectEntity;
import de.unihamburg.daibetes.api.project.training.federated.CreateProjectFederatedExperimentDTO;
import de.unihamburg.daibetes.api.workflow.WorkflowEntity;
import de.unihamburg.daibetes.helper.QuarkusMappingConfig;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Mappings;
import org.mapstruct.NullValuePropertyMappingStrategy;

import java.util.Set;

@Mapper(config = QuarkusMappingConfig.class)
public interface ExecutionExperimentMapper {

    @Mappings({
            @Mapping(target = "federatedAppId", source = "federatedAppVersion.federatedApp.id"),
            @Mapping(target = "federatedAppVersionId", source = "federatedAppVersion.id"),
            @Mapping(target = "federatedAppVersionName", source = "federatedAppVersion.versionFormatted"),
            @Mapping(target = "projectId", source = "project.id"),
            @Mapping(target = "workflowId", source = "workflow.id")
    })
    ExecutionExperimentDTO entityToDto(ExecutionExperimentEntity entity);

    // A developer's experiment with a version of an app.
    @BeanMapping(ignoreByDefault = true)
    @Mappings({
            @Mapping(target = "name", source = "create.name"),
            @Mapping(target = "description", source = "create.description"),
            @Mapping(target = "diagramConfigs", source = "create.diagramConfigs", defaultExpression = "java(new java.util.ArrayList<>())"),
            @Mapping(target = "keycloakId", source = "keycloakId"),
            @Mapping(target = "federatedAppVersion", source = "federatedAppVersion")
    })
    ExecutionExperimentEntity createDtoToEntity(CreateExperimentDTO create, String keycloakId, FederatedAppVersionEntity federatedAppVersion);

    // What the developer edits on an experiment: its name, description and charts.
    @BeanMapping(ignoreByDefault = true)
    @Mappings({
            @Mapping(target = "name", source = "name", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE),
            @Mapping(target = "description", source = "description", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.SET_TO_NULL),
            @Mapping(target = "diagramConfigs", source = "diagramConfigs", defaultExpression = "java(new java.util.ArrayList<>())")
    })
    void updateEntity(ExecutionExperimentDTO update, @MappingTarget ExecutionExperimentEntity experiment);

    // A training of a project on the platform, or a test of it.
    @BeanMapping(ignoreByDefault = true)
    @Mappings({
            @Mapping(target = "name", source = "name"),
            @Mapping(target = "description", source = "description"),
            @Mapping(target = "test", source = "test"),
            @Mapping(target = "keycloakId", source = "keycloakId"),
            @Mapping(target = "project", source = "project")
    })
    ExecutionExperimentEntity projectToExperimentEntity(String name, String description, boolean test, String keycloakId, ProjectEntity project);

    // A training across clinics, waiting for them to accept it.
    @BeanMapping(ignoreByDefault = true)
    @Mappings({
            @Mapping(target = "name", source = "create.name"),
            @Mapping(target = "description", source = "create.description"),
            @Mapping(target = "modelNeedToBePublic", source = "create.modelNeedToBePublic"),
            @Mapping(target = "keycloakId", source = "keycloakId"),
            @Mapping(target = "project", source = "project"),
            @Mapping(target = "projectVersion", source = "projectVersion"),
            @Mapping(target = "workflow", source = "workflow"),
            @Mapping(target = "relayServerAddress", source = "relayServerAddress"),
            @Mapping(target = "channelId", source = "channelId"),
            @Mapping(target = "globalUniqueId", expression = "java(java.util.UUID.randomUUID().toString())"),
            @Mapping(target = "status", constant = "INIT"),
            @Mapping(target = "acceptanceCount", constant = "0L"),
            @Mapping(target = "acceptanceClinicCount", constant = "0L"),
            @Mapping(target = "modelCanBePublic", constant = "true")
    })
    ExecutionExperimentEntity federatedCreateDtoToEntity(CreateProjectFederatedExperimentDTO create, String keycloakId, ProjectEntity project,
                                                         ProjectDetailDTO projectVersion, WorkflowEntity workflow,
                                                         String relayServerAddress, String channelId);

    // What the clinics are asked to accept.
    @BeanMapping(ignoreByDefault = true)
    @Mappings({
            @Mapping(target = "id", source = "experiment.id"),
            @Mapping(target = "keycloakId", source = "experiment.keycloakId"),
            @Mapping(target = "globalUniqueId", source = "experiment.globalUniqueId"),
            @Mapping(target = "name", source = "experiment.name"),
            @Mapping(target = "description", source = "experiment.description"),
            @Mapping(target = "modelNeedToBePublic", source = "experiment.modelNeedToBePublic"),
            @Mapping(target = "projectVersion", source = "experiment.projectVersion"),
            @Mapping(target = "channelId", source = "experiment.channelId"),
            @Mapping(target = "roles", source = "roles"),
            @Mapping(target = "workflow", source = "workflow")
    })
    ProjectFederatedExperimentForLocalDTO experimentToLearningQuery(ExecutionExperimentEntity experiment, Set<String> roles, WorkflowDTO workflow);
}
