package bio.cosy.feddb.local.api.learning.request;

import bio.cosy.feddb.core.api.project.ProjectDetailDTO;
import bio.cosy.feddb.core.base.BaseMapper;
import bio.cosy.feddb.local.api.cohort.patient.traceability.learning.PatientLearningDTO;
import bio.cosy.feddb.local.api.cohort.patient.traceability.learning.PatientLearningEntity;
import bio.cosy.feddb.local.api.cohort.patient.traceability.learning.PatientLearningMapper;
import bio.cosy.feddb.local.api.learning.project.LearningProjectEntity;
import bio.cosy.feddb.local.api.learning.project.LearningProjectMapper;
import bio.cosy.feddb.local.helper.QuarkusMappingConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;
import org.mapstruct.Named;
import org.mapstruct.factory.Mappers;

import java.util.List;
import java.util.Set;

@Mapper(config = QuarkusMappingConfig.class)
public interface LearningRequestMapper extends BaseMapper<LearningRequestDTO, LearningRequestEntity> {

    LearningProjectMapper projectAppMapper = Mappers.getMapper(LearningProjectMapper.class);
    PatientLearningMapper projectLearningMapper = Mappers.getMapper(PatientLearningMapper.class);

    @Mappings({
            @Mapping(target = "project", source = "project", qualifiedByName = "mapProject"),
            @Mapping(target = "requestPatients", source = "patients", qualifiedByName = "mapPatients"),
            @Mapping(target = "patientCountByCohort", ignore = true),
            @Mapping(target = "cohortDecisions", ignore = true),
            @Mapping(target = "awaitingCurrentUserDecision", ignore = true),
            @Mapping(target = "platformIsCoordinator", source = "project.platformIsCoordinator"),
            @Mapping(target = "executionMode", source = "project.executionMode"),
            @Mapping(target = "globalFLExperimentUniqueId", source = "globalExperimentUniqueId")
    })
    LearningRequestDTO entityToDto(LearningRequestEntity entity);

    @Named("toSummary")
    @Mappings({
            @Mapping(target = "project", source = "project", qualifiedByName = "mapProject"),
            @Mapping(target = "requestPatients", ignore = true),
            @Mapping(target = "patientCountByCohort", ignore = true),
            @Mapping(target = "cohortDecisions", ignore = true),
            @Mapping(target = "awaitingCurrentUserDecision", ignore = true),
            @Mapping(target = "platformIsCoordinator", source = "project.platformIsCoordinator"),
            @Mapping(target = "executionMode", source = "project.executionMode"),
            @Mapping(target = "globalFLExperimentUniqueId", source = "globalExperimentUniqueId")
    })
    LearningRequestDTO entityToSummaryDto(LearningRequestEntity entity);

    @Mappings({
            @Mapping(target = "description", ignore = true),
            @Mapping(target = "name", ignore = true),
            @Mapping(target = "platformUserId", ignore = true),
            @Mapping(target = "project", ignore = true),
            @Mapping(target = "patients", ignore = true),
            @Mapping(target = "globalExperimentUniqueId", source = "globalFLExperimentUniqueId")
    })
    LearningRequestEntity dtoToEntity(LearningRequestDTO sourceCode);

    @Named("mapProject")
    default ProjectDetailDTO mapProject(LearningProjectEntity project) {
        if (project == null) {
            return null;
        }
        return projectAppMapper.entityToDto(project);
    }

    @Named("mapPatients")
    default List<PatientLearningDTO> mapPatients(Set<PatientLearningEntity> patients) {
        if (patients == null) {
            return null;
        }
        return projectLearningMapper.entitiesToDtos(patients);
    }
}
