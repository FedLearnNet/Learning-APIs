package bio.cosy.feddb.local.api.cohort.patient.dataentry;


import bio.cosy.feddb.local.api.cohort.patient.dataentry.metadata.MetaPatientDataEntryEntity;
import bio.cosy.feddb.local.helper.QuarkusMappingConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;

import java.util.Set;

@Mapper(config = QuarkusMappingConfig.class)
public interface PatientDataMapper {

    @Mappings({
            @Mapping(target = "id", ignore = true),
            @Mapping(target = "version", ignore = true),
            @Mapping(target = "patient", ignore = true),
    })
    PatientDataEntryEntity entityCopy(PatientDataEntryEntity entry);


    @Mappings({
            @Mapping(target = "id", ignore = true),
            @Mapping(target = "version", ignore = true),
    })
    MetaPatientDataEntryEntity metaCopy(MetaPatientDataEntryEntity meta);

    Set<MetaPatientDataEntryEntity> metaSetCopy(Set<MetaPatientDataEntryEntity> metaSet);
}
