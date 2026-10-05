package bio.cosy.feddb.local.agent.etl.transform.helper;

import bio.cosy.feddb.local.agent.etl.transform.assistant.TransformerAssistantMessageDTO;
import bio.cosy.feddb.local.agent.etl.transform.model.ConnectorSelectedTransformer;
import bio.cosy.feddb.local.api.importer.connector.ConnectorDTO;
import bio.cosy.feddb.local.api.importer.connector.ConnectorHelper;
import bio.cosy.feddb.local.api.importer.functions.managed.AbstractManagedFunction;
import bio.cosy.feddb.local.api.importer.mapping.ConnectorMappingDTO;
import bio.cosy.feddb.local.api.importer.transformer.ConnectorTransformerDTO;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.*;

public class TransformerHelper {
    public static ConnectorDTO toConnector(Collection<TransformerAssistantMessageDTO> results) {
        List<ConnectorTransformerDTO> transformers = new ArrayList<>();
        List<ConnectorMappingDTO> mappings = new ArrayList<>();
        for (TransformerAssistantMessageDTO result : results) {
            ConnectorSelectedTransformer selected = result.getSelectedTransformer();
            if (selected == null || !selected.isFoundTransformer()) {
                continue;
            }
            transformers.addAll(selected.getSelectedTransformers());
            ConnectorMappingDTO mapping = new ConnectorMappingDTO();
            mapping.setColumn(selected.getMappedColumn());
            mapping.setMapping(result.getMappingPath());
            mapping.setSchemaId(result.getSchemaId());
            mappings.add(mapping);
        }
        ConnectorDTO connector = new ConnectorDTO();
        connector.setTransformer(group(transformers));
        connector.setSchemaMapping(mappings);
        return connector;
    }

    public static List<ConnectorTransformerDTO> group(List<ConnectorTransformerDTO> transformers) {
        ObjectMapper objectMapper = new ObjectMapper();
        List<ConnectorTransformerDTO> grouped = new ArrayList<>();
        for (ConnectorTransformerDTO transformer : transformers) {
            ConnectorTransformerDTO target = groupTarget(grouped, transformer);
            if (target == null) {
                grouped.add(objectMapper.convertValue(transformer, ConnectorTransformerDTO.class));
            } else {
                LinkedHashSet<String> columns = new LinkedHashSet<>(ConnectorHelper.commaSeparated(target.getColumn()));
                columns.addAll(ConnectorHelper.commaSeparated(transformer.getColumn()));
                target.setColumn(String.join(",", columns));
            }
        }
        return grouped;
    }

    public static ConnectorTransformerDTO groupTarget(List<ConnectorTransformerDTO> grouped, ConnectorTransformerDTO transformer) {
        if (!isCellTransformer(transformer)) {
            return null;
        }
        Set<String> columns = ConnectorHelper.commaSeparated(transformer.getColumn());
        for (int index = grouped.size() - 1; index >= 0; index--) {
            ConnectorTransformerDTO candidate = grouped.get(index);
            if (isCellTransformer(candidate)
                    && Objects.equals(candidate.getModuleName(), transformer.getModuleName())
                    && Objects.equals(candidate.getMethodName(), transformer.getMethodName())
                    && Objects.equals(candidate.getInputMapping(), transformer.getInputMapping())
                    && Objects.equals(candidate.getHyperparams(), transformer.getHyperparams())) {
                return candidate;
            }
            Set<String> touched = new HashSet<>(ConnectorHelper.transformerInputs(candidate));
            touched.addAll(ConnectorHelper.transformerOutputs(candidate));
            if (touched.stream().anyMatch(columns::contains)) {
                return null;
            }
        }
        return null;
    }

    public static boolean isCellTransformer(ConnectorTransformerDTO transformer) {
        return transformer.getColumn() != null && !transformer.getColumn().isBlank()
                && (transformer.getInputMapping() == null || transformer.getInputMapping().values().stream()
                .allMatch(value -> String.valueOf(value).startsWith(AbstractManagedFunction.VALUE_PREFIX)))
                && (transformer.getReturnMapping() == null || transformer.getReturnMapping().isEmpty())
                && (transformer.getAppImage() == null || transformer.getAppImage().isBlank());
    }


}
