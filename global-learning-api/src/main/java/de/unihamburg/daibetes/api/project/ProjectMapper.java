package de.unihamburg.daibetes.api.project;

import bio.cosy.feddb.core.api.file.FileDTO;
import bio.cosy.feddb.core.api.project.ProjectDTO;
import bio.cosy.feddb.core.api.project.ProjectDetailDTO;
import bio.cosy.feddb.core.api.project.ProjectStatus;
import bio.cosy.feddb.core.base.BaseMapper;
import de.unihamburg.daibetes.api.file.FileEntity;
import de.unihamburg.daibetes.api.file.FileMapper;
import de.unihamburg.daibetes.api.project.experiment.federated.ProjectFederatedExperimentEntity;
import de.unihamburg.daibetes.api.project.experiment.federated.participants.ProjectFederatedExperimentParticipantEntity;
import de.unihamburg.daibetes.api.query.QueryEntity;
import de.unihamburg.daibetes.api.workflow.WorkflowEntity;
import de.unihamburg.daibetes.helper.QuarkusMappingConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;
import org.mapstruct.Named;
import org.mapstruct.factory.Mappers;

import java.util.Objects;
import java.util.Set;

@Mapper(config = QuarkusMappingConfig.class)
public interface ProjectMapper extends BaseMapper<ProjectDetailDTO, ProjectEntity> {

    FileMapper fileMapper = Mappers.getMapper(FileMapper.class);

    @Mapping(target = "globalUniqueQueryId", source = "query.globalUniqueId")
    @Mapping(target = "workflowId", source = "entity", qualifiedByName = "toWorkflowId")
    @Mapping(target = "isCoordinator", ignore = true)
    @Mapping(target = "status", source = "experiments", qualifiedByName = "mapProjectStatus")
    @Mapping(target = "queryId", source = "query.id")
    @Mapping(target = "role", ignore = true)
    @Mapping(target = "file", source = "file", qualifiedByName = "toFileDTO")
    ProjectDetailDTO entityToDto(ProjectEntity entity);


    @Mapping(target = "experiments", ignore = true)
    @Mapping(target = "query", source = "queryId", qualifiedByName = "toQuery")
    @Mapping(target = "memberships", ignore = true)
    @Mapping(target = "workflow", source = "workflowId", qualifiedByName = "toWorkflow")
    @Mapping(target = "file", source = "file", qualifiedByName = "toFile")
    ProjectEntity dtoToEntity(ProjectDetailDTO dto);

    @Named("toFileDTO")
    default FileDTO toFile(FileEntity file) {
        if (file == null) {
            return null;
        }
        return fileMapper.entityToDto(file);
    }

    @Named("toFile")
    default FileEntity toFile(FileDTO file) {
        if (file == null || file.getId() == null) {
            return null;
        }
        FileEntity fileEntity = new FileEntity();
        fileEntity.setId(file.getId());
        return fileEntity;
    }

    @Named("toQuery")
    default QueryEntity toQuery(Long id) {
        if (id == null) {
            return null;
        }
        QueryEntity queryEntity = new QueryEntity();
        queryEntity.setId(id);
        return queryEntity;
    }

    @Named("toWorkflowId")
    default Long toWorkflowId(ProjectEntity entity) {
        if (entity == null || entity.getWorkflow() == null) {
            return null;
        }
        return entity.getWorkflow().getId();
    }

    @Named("toWorkflow")
    default WorkflowEntity toWorkflow(Long workflowId) {
        if (workflowId == null) {
            return null;
        }
        WorkflowEntity entity = new WorkflowEntity();
        entity.setId(workflowId);
        return entity;
    }

    @Mappings({
            @Mapping(target = "version", ignore = true),
            @Mapping(target = "verifiedOn", ignore = true),
            @Mapping(target = "updatedAt", ignore = true),
            @Mapping(target = "role", ignore = true),
            @Mapping(target = "id", ignore = true),
            @Mapping(target = "certificationLevel", ignore = true),
            @Mapping(target = "audited", ignore = true),
            @Mapping(target = "createdAt", ignore = true),
            @Mapping(target = "status", ignore = true),
            @Mapping(target = "coordinatorHasData", ignore = true),
            @Mapping(target = "isCoordinator", ignore = true),
            @Mapping(target = "platformIsCoordinator", ignore = true),
            @Mapping(target = "workflowId", ignore = true),
            @Mapping(target = "file", ignore = true),
            @Mapping(target = "globalUniqueQueryId", ignore = true),
            @Mapping(target = "exportConfig", ignore = true)
    })
    ProjectDetailDTO createToDTO(ProjectCreateDTO projectCreateDTO);

    @Mappings({

            @Mapping(target = "role", ignore = true),
            @Mapping(target = "queryId", source = "entity.query.id"),
            @Mapping(target = "status", source = "experiments", qualifiedByName = "mapProjectStatus"),
            @Mapping(target = "workflowId", ignore = true),
            @Mapping(target = "globalUniqueQueryId", source = "entity.query.globalUniqueId"),
            @Mapping(target = "file", source = "file", qualifiedByName = "toFileDTO")
    })
    ProjectDTO entityToSimpleDTO(ProjectEntity entity);

    @Named("mapProjectStatus")
    default ProjectStatus mapProjectStatus(Set<ProjectFederatedExperimentEntity> experiments) {
        if (experiments == null) {
            return ProjectStatus.INIT;
        }
        return experiments.stream()
                .map(ProjectFederatedExperimentEntity::getParticipants)
                .filter(participants -> !participants.isEmpty())
                .findFirst()
                .flatMap(participants -> participants.stream()
                        .map(ProjectFederatedExperimentParticipantEntity::getStepStatus)
                        .filter(Objects::nonNull)
                        .findFirst())
                .orElse(ProjectStatus.INIT);
    }
}
