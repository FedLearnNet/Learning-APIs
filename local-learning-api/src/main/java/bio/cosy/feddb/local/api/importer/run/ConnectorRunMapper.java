package bio.cosy.feddb.local.api.importer.run;

import bio.cosy.feddb.core.base.BaseMapper;
import bio.cosy.feddb.local.helper.QuarkusMappingConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;

@Mapper(config = QuarkusMappingConfig.class)
public interface ConnectorRunMapper extends BaseMapper<ConnectorRunDTO, ConnectorRunEntity> {

    @Mappings({
            @Mapping(target = "cohortId", source = "cohort.id"),
            @Mapping(target = "connectorId", source = "connector.id")
    })
    ConnectorRunDTO entityToDto(ConnectorRunEntity entity);

    @Mappings({
            @Mapping(target = "cohort.id", source = "cohortId"),
            @Mapping(target = "connector.id", source = "connectorId"),
            @Mapping(target = "steps", ignore = true),
            @Mapping(target = "errorLogs", ignore = true),
            @Mapping(target = "messages", ignore = true)
    })
    ConnectorRunEntity dtoToEntity(ConnectorRunDTO dto);

}
