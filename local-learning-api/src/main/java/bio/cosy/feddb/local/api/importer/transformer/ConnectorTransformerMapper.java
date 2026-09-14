package bio.cosy.feddb.local.api.importer.transformer;

import bio.cosy.feddb.core.base.BaseMapper;
import bio.cosy.feddb.local.helper.QuarkusMappingConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Mappings;

@Mapper(config = QuarkusMappingConfig.class)
public interface ConnectorTransformerMapper extends BaseMapper<ConnectorTransformerDTO, ConnectorTransformerEntity> {

    @Mappings({
            @Mapping(target = "connectorId", source = "connector.id")
    })
    ConnectorTransformerDTO entityToDto(ConnectorTransformerEntity entity);

    @Mappings({
            @Mapping(target = "connector.id", source = "connectorId")
    })
    ConnectorTransformerEntity dtoToEntity(ConnectorTransformerDTO dto);

    @Mappings({
            @Mapping(target = "id", ignore = true),
            @Mapping(target = "version", ignore = true),
            @Mapping(target = "createdAt", ignore = true),
            @Mapping(target = "updatedAt", ignore = true),
            @Mapping(target = "connector", ignore = true),
            @Mapping(target = "position", ignore = true),
    })
    void updateContentFromDto(ConnectorTransformerDTO dto, @MappingTarget ConnectorTransformerEntity entity);

}
