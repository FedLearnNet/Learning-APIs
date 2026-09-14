package bio.cosy.feddb.local.api.cohort.patient.traceability.learning;

import bio.cosy.feddb.core.base.BaseMapper;
import bio.cosy.feddb.local.helper.QuarkusMappingConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;

@Mapper(config = QuarkusMappingConfig.class)
public interface PatientLearningMapper extends BaseMapper<PatientLearningDTO, PatientLearningEntity> {

    @Mappings({
            @Mapping(target = "patientId", source = "patient.id"),
            @Mapping(target = "requestId", source = "request.id"),
            @Mapping(target = "internalCohortId", source = "patient.cohort.id"),
            @Mapping(target = "internalPatientId", source = "patient.id"),
            @Mapping(target = "cohortName", source = "patient.cohort.name"),
            @Mapping(target = "externalPatientId", source = "patient.externalPatientId"),
            @Mapping(target = "projectName", source = "request.project.name"),
    })
    PatientLearningDTO entityToDto(PatientLearningEntity entity);

    @Mappings({
            @Mapping(target = "patient.id", source = "patientId"),
            @Mapping(target = "request.id", source = "requestId"),
    })
    PatientLearningEntity dtoToEntity(PatientLearningDTO dto);


}
