package bio.cosy.feddb.local.api.importer.run.message;

import bio.cosy.feddb.core.base.BaseMapper;
import bio.cosy.feddb.local.helper.QuarkusMappingConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;

@Mapper(config = QuarkusMappingConfig.class)
public interface ConnectorRunMessagesMapper extends BaseMapper<ConnectorRunMessagesDTO, ConnectorRunMessagesEntity> {

    @Mappings({
            @Mapping(target = "runId", source = "run.id"),
            @Mapping(target = "transformerId", source = "transformer.id")
    })
    ConnectorRunMessagesDTO entityToDto(ConnectorRunMessagesEntity entity);

    // Log lines are written by the import, never taken over from a request.
    @Mappings({
            @Mapping(target = "run", ignore = true),
            @Mapping(target = "transformer", ignore = true)
    })
    ConnectorRunMessagesEntity dtoToEntity(ConnectorRunMessagesDTO dto);
}
