package bio.cosy.feddb.local.api.importer.transformer.session;

import bio.cosy.feddb.core.api.run.StartRunDTO;
import bio.cosy.feddb.local.api.importer.connector.ConnectorDTO;
import bio.cosy.feddb.local.api.importer.run.step.ConnectorRunStepDTO;
import bio.cosy.feddb.local.api.importer.transformer.ConnectorTransformerDTO;
import bio.cosy.feddb.local.helper.QuarkusMappingConfig;
import io.quarkus.logging.Log;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Mapper(config = QuarkusMappingConfig.class)
public interface AppTransformerMapper {

    String INPUT_VARIABLE = "input";

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "connectorRunId", source = "connectorRunId")
    @Mapping(target = "transformationId", source = "transformer.id")
    @Mapping(target = "status", constant = "PENDING")
    @Mapping(target = "progress", constant = "0f")
    @Mapping(target = "hyperParams", source = "transformer", qualifiedByName = "hyperParams")
    ConnectorRunStepDTO toStep(ConnectorTransformerDTO transformer, Long connectorRunId);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "id", source = "batchId")
    @Mapping(target = "status", constant = "PENDING")
    @Mapping(target = "hyperParams", source = "transformer", qualifiedByName = "hyperParams")
    @Mapping(target = "inputData", source = "rows", qualifiedByName = "inputData")
    @Mapping(target = "inputFilePaths", expression = "java(new java.util.LinkedHashMap<>())")
    StartRunDTO toBatch(Long batchId, ConnectorTransformerDTO transformer, List<Map<String, Object>> rows);

    @Named("hyperParams")
    default LinkedHashMap<String, Object> hyperParams(ConnectorTransformerDTO transformer) {
        LinkedHashMap<String, Object> params = new LinkedHashMap<>();
        if (transformer.getInputMapping() != null && !transformer.getInputMapping().isEmpty()) {
            params.put("input_mapping", transformer.getInputMapping());
        }
        if (transformer.getReturnMapping() != null && !transformer.getReturnMapping().isEmpty()) {
            params.put("return_mapping", transformer.getReturnMapping());
        }
        if (transformer.getColumn() != null && !transformer.getColumn().isBlank()) {
            params.put("column", transformer.getColumn());
        }
        if (transformer.getHyperparams() != null) {
            params.putAll(transformer.getHyperparams());
        }
        return params;
    }

    @Named("inputData")
    default LinkedHashMap<String, Object> inputData(List<Map<String, Object>> rows) {
        LinkedHashMap<String, Object> inputData = new LinkedHashMap<>();
        inputData.put(INPUT_VARIABLE, rows);
        return inputData;
    }

    default List<Map<String, Object>> merge(List<Map<String, Object>> sent, List<Map<String, Object>> returned,
                                            ConnectorTransformerDTO transformer) {
        if (returned.size() != sent.size()) {
            Log.debugf("App transformer '%s' returned %d row(s) for %d sent; taking its rows as they are",
                    transformer.getAppImage(), returned.size(), sent.size());
            return new ArrayList<>(returned);
        }
        List<Map<String, Object>> merged = new ArrayList<>(sent.size());
        for (int index = 0; index < sent.size(); index++) {
            Map<String, Object> row = new LinkedHashMap<>(sent.get(index));
            row.putAll(returned.get(index));
            merged.add(row);
        }
        return merged;
    }

    default List<ConnectorTransformerDTO> appTransformersOf(ConnectorDTO connector) {
        if (connector == null || connector.getTransformer() == null) {
            return List.of();
        }
        return connector.getTransformer().stream()
                .filter(Objects::nonNull)
                .filter(this::isApp)
                .toList();
    }

    default boolean isApp(ConnectorTransformerDTO transformer) {
        return transformer != null && transformer.getAppImage() != null && !transformer.getAppImage().isBlank();
    }
}
