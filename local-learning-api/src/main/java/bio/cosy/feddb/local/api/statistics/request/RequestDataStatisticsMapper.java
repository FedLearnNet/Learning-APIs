package bio.cosy.feddb.local.api.statistics.request;

import bio.cosy.feddb.core.base.BaseEntity;
import bio.cosy.feddb.core.base.BaseMapper;
import bio.cosy.feddb.local.api.cohort.patient.PatientEntity;
import bio.cosy.feddb.local.api.cohort.patient.traceability.query.QueryPatientEntity;
import bio.cosy.feddb.local.helper.QuarkusMappingConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;
import org.mapstruct.Named;

import java.util.Collections;
import java.util.List;

@Mapper(config = QuarkusMappingConfig.class)
public interface RequestDataStatisticsMapper extends BaseMapper<RequestDataStatisticsDTO, RequestDataStatisticsEntity> {

    @Mappings({
            @Mapping(target = "queryId", source = "query.id"),
            @Mapping(target = "cohortIds", source = "entity", qualifiedByName = "getCohortIds"),
            @Mapping(target = "patientIds", source = "entity", qualifiedByName = "getPatientIds")
    })
    RequestDataStatisticsDTO entityToDto(RequestDataStatisticsEntity entity);

    @Mappings({
            @Mapping(target = "query.id", source = "queryId")
    })
    RequestDataStatisticsEntity dtoToEntity(RequestDataStatisticsDTO dto);


    @Named("getCohortIds")
    default List<Long> getCohortIds(RequestDataStatisticsEntity entity) {
        if (entity == null || entity.getQuery() == null) {
            return Collections.emptyList();
        }

        if (entity.getQuery().getPatients() == null || entity.getQuery().getPatients().isEmpty()) {
            return entity.getQuery().getQueriedCohortIds() == null
                    ? Collections.emptyList()
                    : entity.getQuery().getQueriedCohortIds().stream().sorted().toList();
        }

        return entity.getQuery().getPatients().stream()
                .map(QueryPatientEntity::getPatient)
                .map(PatientEntity::getCohort)
                .map(BaseEntity::getId)
                .distinct()
                .sorted()
                .toList();
    }

    @Named("getPatientIds")
    default List<Long> getPatientIds(RequestDataStatisticsEntity entity) {
        if (entity == null || entity.getQuery() == null ||
                entity.getQuery().getPatients() == null || entity.getQuery().getPatients().isEmpty()) {
            return Collections.emptyList();
        }
        return entity.getQuery().getPatients().stream()
                .map(QueryPatientEntity::getPatient)
                .map(BaseEntity::getId)
                .toList();
    }


}
