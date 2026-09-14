package bio.cosy.feddb.local.api.cohort.patient.dataentry;

import bio.cosy.feddb.local.helper.QuarkusMappingConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;
import org.mapstruct.Named;

import java.time.Instant;


@Mapper(config = QuarkusMappingConfig.class)
public interface PatientDataEntryExportMapper {


    @Mappings({
            @Mapping(target = "name", ignore = true),
            @Mapping(target = "patientId", source = "patient.id"),
            @Mapping(target = "value", source = "entity", qualifiedByName = "mapValue"),
            @Mapping(target = "ontologyId", source = "schemaNode.ontology.globalId"),
            @Mapping(target = "datatypeId", source = "schemaNode.dataType.globalDataTypeId"),
            @Mapping(target = "importSchemaGroupId", source = "importSchemaGroupId"),
            @Mapping(target = "visitTimestamp", source = "visitTimestamp", qualifiedByName = "mapVisitTimestamp"),
    })
    PatientDataEntryExportDTO toPatientDataEntryExportDTO(PatientDataEntryEntity entity);

    @Named("mapValue")
    default Object mapValue(PatientDataEntryEntity entity) {
        return PatientDataEntryHelper.getValue(entity, entity.getSchemaNode().getDataType().getType());
    }

    @Named("mapVisitTimestamp")
    default String mapVisitTimestamp(Instant visitTimestamp) {
        return visitTimestamp != null ? visitTimestamp.toString() : null;
    }
}
