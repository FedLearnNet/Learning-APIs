package bio.cosy.feddb.local.api.importer.functions;

import bio.cosy.feddb.local.api.importer.functions.managed.AbstractManagedFunction;
import bio.cosy.feddb.local.api.importer.transformer.ConnectorTransformerDTO;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class FunctionRunnerBO {

    @Inject
    FunctionRegistry registry;

    public boolean exists(String moduleName, String methodName) {
        return registry.get(moduleName, methodName).isPresent();
    }

    public FunctionExecutionMode mode(ConnectorTransformerDTO transformer) {
        if (transformer == null) {
            throw new IllegalArgumentException("transformer must not be null");
        }
        return getFunction(transformer.getModuleName(), transformer.getMethodName()).mode();
    }

    public List<Map<String, Object>> apply(ConnectorTransformerDTO transformer,
                                           List<Map<String, Object>> rows,
                                           String cohortId, Long runId) {
        if (transformer == null) {
            throw new IllegalArgumentException("transformer must not be null");
        }

        return apply(
                transformer.getModuleName(),
                transformer.getMethodName(),
                rows,
                transformer.getColumn(),
                transformer.getInputMapping(),
                transformer.getReturnMapping(),
                transformer.getHyperparams(),
                cohortId,
                transformer.getId(),
                runId
        );
    }

    public List<Map<String, Object>> apply(String moduleName,
                                           String methodName,
                                           List<Map<String, Object>> rows,
                                           String column,
                                           Map<String, Object> inputMapping,
                                           Map<String, Object> returnMapping,
                                           Map<String, Object> hyperparams,
                                           String cohortId,
                                           Long transfromerId,
                                           Long runId) {
        AbstractManagedFunction function = getFunction(moduleName, methodName);

        return function.apply(
                rows,
                column,
                toStringMap(inputMapping),
                toStringMap(returnMapping),
                hyperparams,
                cohortId,
                transfromerId,
                runId
        );
    }

    private AbstractManagedFunction getFunction(String moduleName, String methodName) {
        return registry.get(moduleName, methodName)
                .orElseThrow(() -> new NotFoundException(
                        "Function not found: " + moduleName + "/" + methodName
                ));
    }

    private Map<String, String> toStringMap(Map<String, Object> mapping) {
        if (mapping == null || mapping.isEmpty()) {
            return Map.of();
        }

        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : mapping.entrySet()) {
            result.put(entry.getKey(), entry.getValue() == null ? null : String.valueOf(entry.getValue()));
        }
        return result;
    }
}
