package bio.cosy.feddb.local.api.cohort.patient.traceability.query;

import bio.cosy.feddb.core.base.BaseMapper;
import bio.cosy.feddb.local.helper.QuarkusMappingConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;

@Mapper(config = QuarkusMappingConfig.class)
public interface QueryPatientMapper extends BaseMapper<QueryPatientDTO, QueryPatientEntity> {

    @Mappings({
            @Mapping(target = "patientId", source = "patient.id"),
            @Mapping(target = "queryId", source = "query.globalQueryId"),
            @Mapping(target = "cohortId", source = "patient.cohort.id")
    })
    QueryPatientDTO entityToDto(QueryPatientEntity entity);

    //IGORE CUZ INTERNAL VALUES NOT PK
    @Mappings({
            @Mapping(target = "patient.id", ignore = true),
            @Mapping(target = "query.id", ignore = true),
    })
    QueryPatientEntity dtoToEntity(QueryPatientDTO dto);


}
