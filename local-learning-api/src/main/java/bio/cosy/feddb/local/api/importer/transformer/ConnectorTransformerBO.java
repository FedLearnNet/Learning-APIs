package bio.cosy.feddb.local.api.importer.transformer;

import bio.cosy.feddb.core.base.BaseBo;
import bio.cosy.feddb.local.api.importer.connector.ConnectorAO;
import bio.cosy.feddb.local.api.importer.connector.ConnectorEntity;
import bio.cosy.feddb.local.api.importer.functions.FunctionRunnerBO;
import bio.cosy.feddb.local.api.importer.run.message.ConnectorRunMessagesAO;
import bio.cosy.feddb.local.api.importer.run.step.ConnectorRunStepAO;
import bio.cosy.feddb.local.api.importer.transformer.session.AppTransformerMapper;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@ApplicationScoped
public class ConnectorTransformerBO extends BaseBo<
        ConnectorTransformerDTO,
        ConnectorTransformerEntity,
        ConnectorTransformerAO,
        ConnectorTransformerMapper> {

    @Inject
    ConnectorAO connectorAO;

    @Inject
    FunctionRunnerBO functionRunnerBO;

    @Inject
    ConnectorRunStepAO runStepAO;

    @Inject
    ConnectorRunMessagesAO runMessagesAO;

    @Inject
    AppTransformerMapper appMapper;

    @Override
    public ConnectorTransformerDTO create(ConnectorTransformerDTO data) {
        Objects.requireNonNull(data, "Transformer must not be null");
        if (!appMapper.isApp(data) && !functionRunnerBO.exists(data.getModuleName(), data.getMethodName())) {
            throw new NotFoundException("Function could not be found");
        }
        ConnectorTransformerEntity entity = connectNodes(data, mapper.dtoToEntity(data));
        return mapper.entityToDto(ao.create(entity));
    }

    public void deleteAllForConnector(Long connectorId) {
        ao.deleteAllForConnector(connectorId);
    }

    // Keeps the ids of unchanged transformers, so the run history that references them stays intact
    public void syncTransformers(Long connectorId, List<ConnectorTransformerDTO> incoming) {
        List<ConnectorTransformerDTO> transformers = incoming == null ? List.of() : incoming;
        List<ConnectorTransformerEntity> existing = ao.getAllForConnector(connectorId);
        Map<Long, ConnectorTransformerEntity> existingById = existing.stream()
                .collect(Collectors.toMap(ConnectorTransformerEntity::getId, Function.identity()));
        Set<Long> keptIds = transformers.stream()
                .map(ConnectorTransformerDTO::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        existing.stream()
                .filter(transformer -> !keptIds.contains(transformer.getId()))
                .forEach(this::safeDelete);

        int position = 1;
        for (ConnectorTransformerDTO transformer : transformers) {
            transformer.setConnectorId(connectorId);
            ConnectorTransformerEntity entity = transformer.getId() == null ? null : existingById.get(transformer.getId());
            if (entity == null) {
                transformer.setPosition(position);
                create(transformer);
            } else {
                updateExisting(entity, transformer, position);
            }
            position++;
        }
    }

    private ConnectorTransformerEntity connectNodes(ConnectorTransformerDTO data, ConnectorTransformerEntity entity) {
        Objects.requireNonNull(data.getConnectorId(), "connectorId must not be null");
        ConnectorEntity connector = connectorAO.findById(data.getConnectorId());
        if (connector == null) {
            throw new NotFoundException("Connector not found");
        }
        entity.setConnector(connector);
        if (data.getColumn() == null) {
            entity.setColumn("");
        }
        if (data.getInputMapping() == null) {
            entity.setInputMapping(Map.of());
        }
        if (data.getReturnMapping() == null) {
            entity.setReturnMapping(Map.of());
        }
        return entity;
    }

    // Run steps and messages keep their logs; only their reference to the transformer is removed
    private void safeDelete(ConnectorTransformerEntity transformer) {
        int detachedSteps = runStepAO.detachTransformer(transformer.getId());
        int detachedMessages = runMessagesAO.detachTransformer(transformer.getId());
        if (detachedSteps > 0 || detachedMessages > 0) {
            Log.infof("Transformer %s had run history — detached %d steps and %d messages before deletion",
                    transformer.getId(), detachedSteps, detachedMessages);
        }
        ao.delete(transformer);
    }

    private void updateExisting(ConnectorTransformerEntity entity, ConnectorTransformerDTO dto, int position) {
        boolean positionChanged = entity.getPosition() == null || entity.getPosition() != position;
        boolean contentChanged = !entity.contentEquals(mapper.dtoToEntity(dto));
        if (!positionChanged && !contentChanged) {
            return;
        }
        entity.setPosition(position);
        if (contentChanged) {
            mapper.updateContentFromDto(dto, entity);
        }
        Log.infof("Transformer %s updated in-place (position=%s, content=%s)", entity.getId(),
                positionChanged ? "changed" : "unchanged", contentChanged ? "changed" : "unchanged");
        ao.persist(entity);
    }
}
