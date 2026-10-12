package bio.cosy.feddb.local.agent.etl.transform.helper;

import bio.cosy.feddb.local.api.importer.connector.ConnectorHelper;
import bio.cosy.feddb.local.api.importer.functions.FunctionExecutionMode;
import bio.cosy.feddb.local.api.importer.functions.FunctionParameterDTO;
import bio.cosy.feddb.local.api.importer.functions.FunctionParameterUsage;
import bio.cosy.feddb.local.api.importer.functions.FunctionRegistry;
import bio.cosy.feddb.local.api.importer.transformer.ConnectorTransformerDTO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static bio.cosy.feddb.local.api.importer.functions.managed.AbstractManagedFunction.VALUE_PREFIX;

@ApplicationScoped
public class TransformerCombiner {

    @Inject
    FunctionRegistry functionRegistry;

    @Inject
    ObjectMapper objectMapper;
    
    public ConnectorTransformerDTO normalize(ConnectorTransformerDTO transformer) {
        Map<String, Object> inputMapping = new LinkedHashMap<>(transformer.getInputMapping() == null ? Map.of() : transformer.getInputMapping());
        Map<String, Object> hyperparams = new LinkedHashMap<>(transformer.getHyperparams() == null ? Map.of() : transformer.getHyperparams());
        functionRegistry.get(transformer.getModuleName(), transformer.getMethodName()).ifPresent(function -> {
            for (FunctionParameterDTO parameter : function.parameters()) {
                String name = parameter.getName();
                if (parameter.getUsage() == FunctionParameterUsage.HYPERPARAMETER) {
                    // A bare [VALUE] is the cell value itself, only a literal can become a hyperparameter
                    if (inputMapping.get(name) instanceof String text && text.startsWith(VALUE_PREFIX) && !text.equals(VALUE_PREFIX)) {
                        inputMapping.remove(name);
                        hyperparams.putIfAbsent(name, text.substring(VALUE_PREFIX.length()));
                    }
                } else if (hyperparams.containsKey(name) && !text(hyperparams.get(name)).isEmpty()) {
                    inputMapping.putIfAbsent(name, VALUE_PREFIX + text(hyperparams.remove(name)));
                }
            }
        });
        transformer.setInputMapping(inputMapping);
        transformer.setHyperparams(hyperparams);
        return transformer;
    }


    public List<ConnectorTransformerDTO> combine(List<ConnectorTransformerDTO> transformers) {
        List<ConnectorTransformerDTO> combined = new ArrayList<>();
        for (ConnectorTransformerDTO original : transformers) {
            ConnectorTransformerDTO transformer = normalize(objectMapper.convertValue(original, ConnectorTransformerDTO.class));
            if (!isCellTransformer(transformer)) {
                combined.add(transformer);
                continue;
            }
            // Every column joins the earliest equal transformer it can, the ones a step in between touches stay here
            LinkedHashSet<String> remaining = new LinkedHashSet<>();
            for (String column : ConnectorHelper.commaSeparated(transformer.getColumn())) {
                ConnectorTransformerDTO target = combineTarget(combined, transformer, column);
                if (target == null) {
                    remaining.add(column);
                } else {
                    LinkedHashSet<String> columns = new LinkedHashSet<>(ConnectorHelper.commaSeparated(target.getColumn()));
                    columns.add(column);
                    target.setColumn(String.join(",", columns));
                }
            }
            if (!remaining.isEmpty()) {
                transformer.setColumn(String.join(",", remaining));
                combined.add(transformer);
            }
        }
        return combined;
    }

    private ConnectorTransformerDTO combineTarget(List<ConnectorTransformerDTO> combined, ConnectorTransformerDTO transformer,
                                                         String column) {
        for (int index = combined.size() - 1; index >= 0; index--) {
            ConnectorTransformerDTO candidate = combined.get(index);
            if (isCellTransformer(candidate)
                    && Objects.equals(candidate.getModuleName(), transformer.getModuleName())
                    && Objects.equals(candidate.getMethodName(), transformer.getMethodName())
                    && Objects.equals(candidate.getInputMapping(), transformer.getInputMapping())
                    && Objects.equals(candidate.getHyperparams(), transformer.getHyperparams())) {
                return candidate;
            }
            Set<String> touched = new HashSet<>(ConnectorHelper.transformerInputs(candidate));
            touched.addAll(ConnectorHelper.transformerOutputs(candidate));
            if (touched.contains(column)) {
                return null;
            }
        }
        return null;
    }

    private boolean isCellTransformer(ConnectorTransformerDTO transformer) {
        return functionRegistry.get(transformer.getModuleName(), transformer.getMethodName())
                .map(function -> function.mode() != FunctionExecutionMode.PATIENT).orElse(true)
                && transformer.isCellBased();
    }

    private String text(Object value) {
        if (value == null) {
            return "null";
        }
        if (!(value instanceof Map || value instanceof Collection)) {
            return String.valueOf(value);
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return String.valueOf(value);
        }
    }
}
