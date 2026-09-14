package bio.cosy.feddb.local.api.learning.request;

import bio.cosy.feddb.core.api.project.ProjectDetailDTO;
import bio.cosy.feddb.core.base.BaseMapper;
import bio.cosy.feddb.local.api.cohort.patient.traceability.learning.PatientLearningDTO;
import bio.cosy.feddb.local.api.cohort.patient.traceability.learning.PatientLearningEntity;
import bio.cosy.feddb.local.api.cohort.patient.traceability.learning.PatientLearningMapper;
import bio.cosy.feddb.local.api.learning.project.FederatedLearningProjectEntity;
import bio.cosy.feddb.local.api.learning.project.FederatedLearningProjectMapper;
import bio.cosy.feddb.local.helper.QuarkusMappingConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;
import org.mapstruct.Named;
import org.mapstruct.factory.Mappers;

import java.util.List;
import java.util.Set;

@Mapper(config = QuarkusMappingConfig.class)
public interface FederatedLearningRequestMapper extends BaseMapper<FederatedLearningRequestDTO, FederatedLearningRequestEntity> {

    FederatedLearningProjectMapper projectAppMapper = Mappers.getMapper(FederatedLearningProjectMapper.class);
    PatientLearningMapper projectLearningMapper = Mappers.getMapper(PatientLearningMapper.class);

    @Mappings({
            @Mapping(target = "project", source = "project", qualifiedByName = "mapProject"),
            @Mapping(target = "requestPatients", source = "patients", qualifiedByName = "mapPatients"),
            @Mapping(target = "cohortDecisions", ignore = true),
            @Mapping(target = "awaitingCurrentUserDecision", ignore = true)
    })
    FederatedLearningRequestDTO entityToDto(FederatedLearningRequestEntity entity);

    @Mappings({
            @Mapping(target = "description", ignore = true),
            @Mapping(target = "name", ignore = true),
            @Mapping(target = "platformUserId", ignore = true),
            @Mapping(target = "project", ignore = true),
            @Mapping(target = "patients", ignore = true)
    })
    FederatedLearningRequestEntity dtoToEntity(FederatedLearningRequestDTO sourceCode);

    @Named("mapProject")
    default ProjectDetailDTO mapProject(FederatedLearningProjectEntity project) {
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
