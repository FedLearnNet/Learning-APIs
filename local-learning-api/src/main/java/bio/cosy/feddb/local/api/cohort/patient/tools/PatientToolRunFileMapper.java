package bio.cosy.feddb.local.api.cohort.patient.tools;

import bio.cosy.feddb.core.api.file.FileDTO;
import bio.cosy.feddb.core.base.BaseMapper;
import bio.cosy.feddb.local.helper.QuarkusMappingConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = QuarkusMappingConfig.class)
public interface PatientToolRunFileMapper extends BaseMapper<FileDTO, PatientToolRunFileEntity> {

    @Mapping(target = "downloadUrl", ignore = true)
    FileDTO entityToDto(PatientToolRunFileEntity entity);

    @Mapping(target = "largeObjectId", ignore = true)
    @Mapping(target = "keycloakId", ignore = true)
    @Mapping(target = "run", ignore = true)
    PatientToolRunFileEntity dtoToEntity(FileDTO dto);
}
