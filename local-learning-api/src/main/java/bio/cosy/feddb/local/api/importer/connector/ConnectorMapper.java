package bio.cosy.feddb.local.api.importer.connector;

import bio.cosy.feddb.core.base.BaseMapper;
import bio.cosy.feddb.local.api.importer.run.ConnectorRunDTO;
import bio.cosy.feddb.local.api.importer.run.ConnectorRunMapper;
import bio.cosy.feddb.local.api.importer.transformer.ConnectorTransformerDTO;
import bio.cosy.feddb.local.api.importer.transformer.ConnectorTransformerMapper;
import bio.cosy.feddb.local.helper.QuarkusMappingConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;
import org.mapstruct.Named;
import org.mapstruct.factory.Mappers;

import java.util.List;

@Mapper(config = QuarkusMappingConfig.class)
public interface ConnectorMapper extends BaseMapper<ConnectorDTO, ConnectorEntity> {

    ConnectorTransformerMapper transformerMapper = Mappers.getMapper(ConnectorTransformerMapper.class);
    ConnectorRunMapper runMapper = Mappers.getMapper(ConnectorRunMapper.class);

    @Mappings({
            @Mapping(target = "cohortId", source = "cohort.id"),
            @Mapping(target = "transformer", source = "entity", qualifiedByName = "mapTransformers"),
            @Mapping(target = "lastRun", source = "entity", qualifiedByName = "mapLastRuns"),
            @Mapping(target = "triggerSettings", source = "entity", qualifiedByName = "mapTriggerSettings"),
    })
    ConnectorDTO entityToDto(ConnectorEntity entity);

    @Mappings({
            @Mapping(target = "cohort.id", source = "cohortId"),
            @Mapping(target = "transformers", ignore = true),
            @Mapping(target = "files", ignore = true),
            @Mapping(target = "runs", ignore = true),
            @Mapping(target = "triggerType", source = "triggerSettings.type"),
            @Mapping(target = "triggerSourceConnectorId", source = "triggerSettings.sourceConnectorId"),
    })
    ConnectorEntity dtoToEntity(ConnectorDTO dto);


    @Named("mapTransformers")
    default List<ConnectorTransformerDTO> mapTransformers(ConnectorEntity entity) {
        return transformerMapper.entitiesToDtos(entity.getTransformers());
    }

    @Named("mapLastRuns")
    default ConnectorRunDTO mapLastRuns(ConnectorEntity entity) {
        if (entity.getRuns() == null || entity.getRuns().isEmpty()) {
            return null;
        }

        return runMapper.entityToDto(entity.getRuns().getFirst());
    }

    @Named("mapTriggerSettings")
    default TriggerSettingsDTO mapTriggerSettings(ConnectorEntity entity) {
        if (entity.getTriggerType() == null) {
            return null;
        }
        return new TriggerSettingsDTO(entity.getTriggerType(), entity.getTriggerSourceConnectorId());
    }
}
