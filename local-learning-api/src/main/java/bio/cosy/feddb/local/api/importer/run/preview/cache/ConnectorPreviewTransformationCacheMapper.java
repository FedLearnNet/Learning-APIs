package bio.cosy.feddb.local.api.importer.run.preview.cache;

import bio.cosy.feddb.core.base.BaseMapper;
import bio.cosy.feddb.local.api.importer.connector.ConnectorEntity;
import bio.cosy.feddb.local.api.importer.transformer.ConnectorTransformerEntity;
import bio.cosy.feddb.local.helper.QuarkusMappingConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;
import org.mapstruct.Named;

@Mapper(config = QuarkusMappingConfig.class)
public interface ConnectorPreviewTransformationCacheMapper
        extends BaseMapper<ConnectorPreviewTransformationCacheDTO, ConnectorPreviewTransformationCacheEntity> {

    @Mappings({
            @Mapping(target = "connectorId", source = "connector.id"),
            @Mapping(target = "transformerId", source = "transformer.id")
    })
    ConnectorPreviewTransformationCacheDTO entityToDto(ConnectorPreviewTransformationCacheEntity entity);

    @Mappings({
            @Mapping(target = "connector", source = "connectorId", qualifiedByName = "mapConnector"),
            @Mapping(target = "transformer", source = "transformerId", qualifiedByName = "mapTransformer")
    })
    ConnectorPreviewTransformationCacheEntity dtoToEntity(ConnectorPreviewTransformationCacheDTO dto);

    @Named("mapConnector")
    default ConnectorEntity mapConnector(Long connectorId) {
        if (connectorId == null) {
            return null;
        }
        ConnectorEntity entity = new ConnectorEntity();
        entity.setId(connectorId);
        return entity;
    }

    @Named("mapTransformer")
    default ConnectorTransformerEntity mapTransformer(Long transformerId) {
        if (transformerId == null) {
            return null;
        }
        ConnectorTransformerEntity entity = new ConnectorTransformerEntity();
        entity.setId(transformerId);
        return entity;
    }
}
