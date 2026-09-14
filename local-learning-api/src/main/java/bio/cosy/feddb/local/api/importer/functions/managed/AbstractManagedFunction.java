package bio.cosy.feddb.local.api.importer.functions.managed;

import bio.cosy.feddb.local.api.importer.functions.FunctionExecutionMode;
import bio.cosy.feddb.local.api.importer.functions.FunctionParameterDTO;
import bio.cosy.feddb.local.api.importer.run.message.ConnectorRunMessageLevels;
import bio.cosy.feddb.local.api.importer.run.message.ConnectorRunRunMessagesBO;
import io.quarkus.logging.Log;
import jakarta.inject.Inject;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public abstract class AbstractManagedFunction {

    @Inject
    ConnectorRunRunMessagesBO logger;

    public String module() {
        String packageName = getClass().getPackageName();
        int lastDot = packageName.lastIndexOf('.');
        return lastDot >= 0 ? packageName.substring(lastDot + 1) : packageName;
    }

    public abstract String methodName();

    public abstract String description();

    public abstract FunctionExecutionMode mode();

    public List<FunctionParameterDTO> parameters() {
        return Collections.emptyList();
    }

    public List<String> returnKeys() {
        return Collections.emptyList();
    }

    public abstract List<Map<String, Object>> apply(List<Map<String, Object>> rows,
                                                    String column,
                                                    Map<String, String> inputMapping,
                                                    Map<String, String> returnMapping,
                                                    Map<String, Object> hyperparams,
                                                    String cohortId,
                                                    Long transformerId,
                                                    Long runId);

    protected Map<String, Object> resolveArgs(Map<String, Object> row,
                                              String column,
                                              Map<String, String> inputMapping,
                                              Map<String, Object> hyperparams) {
        Map<String, Object> args = new LinkedHashMap<>();
        if (hyperparams != null) {
            args.putAll(hyperparams);
        }
        if (inputMapping == null) {
            return args;
        }
        for (Map.Entry<String, String> entry : inputMapping.entrySet()) {
            String paramName = entry.getKey();
            String spec = entry.getValue();
            args.put(paramName, resolveMappingValue(spec, row, column));
        }
        return args;
    }

    private Object resolveMappingValue(String spec, Map<String, Object> row, String column) {
        if ("[VALUE]".equals(spec)) {
            return column == null ? null : row.get(column);
        }
        if (spec.startsWith("[VALUE]")) {
            return spec.substring("[VALUE]".length());
        }
        if (row.containsKey(spec)) {
            return row.get(spec);
        }
        return spec;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof AbstractManagedFunction other)) {
            return false;
        }
        return module().equals(other.module()) && methodName().equals(other.methodName());
    }

    @Override
    public int hashCode() {
        return (module() + "::" + methodName()).hashCode();
    }


    public void logInfo(String message, Long transformerId, Long runId) {
        if (runId == null) {
            Log.debugf("No run to log the ETL message against (transformer %s): %s", transformerId, message);
        } else {
            logger.createTransformerTransactional(message, ConnectorRunMessageLevels.INFO, transformerId, runId);
        }
    }
}
