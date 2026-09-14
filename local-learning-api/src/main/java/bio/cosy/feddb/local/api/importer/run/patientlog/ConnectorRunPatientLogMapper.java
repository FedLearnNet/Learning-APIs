package bio.cosy.feddb.local.api.importer.run.patientlog;

import bio.cosy.feddb.core.base.BaseMapper;
import bio.cosy.feddb.local.helper.QuarkusMappingConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;

@Mapper(config = QuarkusMappingConfig.class)
public interface ConnectorRunPatientLogMapper extends BaseMapper<ConnectorRunPatientLogDTO, ConnectorRunPatientLogEntity> {

    @Mappings({
            @Mapping(target = "runId", source = "run.id")
    })
    ConnectorRunPatientLogDTO entityToDto(ConnectorRunPatientLogEntity entity);

    @Mappings({
            @Mapping(target = "run.id", source = "runId")
    })
    ConnectorRunPatientLogEntity dtoToEntity(ConnectorRunPatientLogDTO dto);
}
