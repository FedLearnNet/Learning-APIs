package bio.cosy.feddb.local.api.importer.run.step;

import bio.cosy.feddb.core.api.run.message.RunMessageLogDTO;
import bio.cosy.feddb.core.api.run.message.RunMessageTypes;
import bio.cosy.feddb.core.base.BaseMapper;
import bio.cosy.feddb.local.api.importer.run.ConnectorRunEntity;
import bio.cosy.feddb.local.api.importer.run.message.ConnectorRunMessagesEntity;
import bio.cosy.feddb.local.api.importer.run.message.ConnectorRunMessagesMapper;
import bio.cosy.feddb.local.api.importer.transformer.ConnectorTransformerEntity;
import bio.cosy.feddb.local.api.learning.project.run.message.RunMessageLogMapper;
import bio.cosy.feddb.local.helper.QuarkusMappingConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;
import org.mapstruct.Named;
import org.mapstruct.factory.Mappers;

import java.util.List;
import java.util.Set;


@Mapper(config = QuarkusMappingConfig.class)
public interface ConnectorRunStepMapper extends BaseMapper<ConnectorRunStepDTO, ConnectorRunStepEntity> {
    RunMessageLogMapper logMapper = Mappers.getMapper(RunMessageLogMapper.class);
    ConnectorRunMessagesMapper messageMapper = Mappers.getMapper(ConnectorRunMessagesMapper.class);

    @Mappings({
            @Mapping(target = "lastLog", source = "entity", qualifiedByName = "getLatestLog"),
            @Mapping(target = "logs", source = "entity.messages", qualifiedByName = "mapRunLogs"),
            @Mapping(target = "connectorRunId", source = "connectorRun.id"),
            @Mapping(target = "transformationId", source = "transformation.id")
    })
    ConnectorRunStepDTO entityToDto(ConnectorRunStepEntity entity);


    @Mappings({
            @Mapping(target = "messages", ignore = true),
            @Mapping(target = "connectorRun", source = "connectorRunId", qualifiedByName = "mapConnectorRun"),
            @Mapping(target = "transformation", source = "transformationId", qualifiedByName = "mapTransformation")
    })
    ConnectorRunStepEntity dtoToEntity(ConnectorRunStepDTO dto);


    @Named("getLatestLog")
    default String getLatestLog(ConnectorRunStepEntity entity) {
        if (entity.getMessages() != null && !entity.getMessages().isEmpty()) {
            return entity.getMessages().stream()
                    .findFirst()
                    .map(ConnectorRunMessagesEntity::getMessage)
                    .orElse(null);
        }
        return null;
    }

    @Named("mapRunLogs")
    default List<RunMessageLogDTO> mapRunLogs(Set<ConnectorRunMessagesEntity> messages) {
        if (messages == null || messages.isEmpty()) {
            return List.of();
        }
        return messages.stream()
                .filter(m -> m.getType().equals(RunMessageTypes.LOG))
                .map(messageMapper::entityToDto)
                .map(logMapper::dtoToLogDTO)
                .toList();
    }

    @Named("mapConnectorRun")
    default ConnectorRunEntity mapConnectorRun(Long connectorRunId) {
        if (connectorRunId == null) {
            return null;
        }
        ConnectorRunEntity entity = new ConnectorRunEntity();
        entity.setId(connectorRunId);
        return entity;
    }

    @Named("mapTransformation")
    default ConnectorTransformerEntity mapTransformation(Long transformationId) {
        if (transformationId == null) {
            return null;
        }
        ConnectorTransformerEntity entity = new ConnectorTransformerEntity();
        entity.setId(transformationId);
        return entity;
    }
}
