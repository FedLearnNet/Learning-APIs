package bio.cosy.feddb.local.api.importer.functions.managed;

import bio.cosy.feddb.local.api.importer.functions.FunctionExecutionMode;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Abstract base class for managed row functions, so functions running on a single row level,
 * returning a map describing new columns and their values.
 * The keys returned must be in the returnKeys().
 * Users using this can then use the returned values either as new columns or overwrite
 * existing columns
 * Extending classes must override:
 * - methodName()
 * - description()
 * - parameters() (Used for both inputs AND hyperparameters!)
 * - returnKeys()
 * - execute(RowFunctionExecutionContext context)
 * To define which inputs the function needs, define them as parameters.
 * The user can then select as input to your parameter a column or a value.
 * If the user selects a column, the RowFunctionExecutionContext context row will
 * contain as key the parameter name and as value the value of the column for that row.
 * So if you have a parameter "input_column" and the user selects the column "age" as input,
 * then for a row where age is 30, context.getRow().get("input_column") will return 30.
 * You can additionally use parameters for hyperparameters,
 * e.g. a parameter "factor" that the user can fill in with a float value,
 * and then context.getRow().get("factor") will return that value given.
 */
public abstract class AbstractManagedRowFunction extends AbstractManagedFunction implements ManagedRowFunction {

    @Override
    public final FunctionExecutionMode mode() {
        return FunctionExecutionMode.ROW;
    }

    @Override
    public List<Map<String, Object>> apply(List<Map<String, Object>> rows,
                                           String column,
                                           Map<String, String> inputMapping,
                                           Map<String, String> returnMapping,
                                           Map<String, Object> hyperparams,
                                           String cohortId,
                                           Long transformerId,
                                           Long runId) {
        for (Map<String, Object> row : rows) {
            Map<String, Object> resolvedArgs = resolveArgs(row, column, inputMapping, hyperparams);

            Map<String, Object> mappedRow = new HashMap<>();
            if (inputMapping != null) {
                for (Map.Entry<String, String> entry : inputMapping.entrySet()) {
                    mappedRow.put(entry.getKey(), row.getOrDefault(entry.getValue(), null));
                }
            }

            RowFunctionExecutionContext rowContext = new RowFunctionExecutionContext();
            rowContext.setParams(resolvedArgs);
            rowContext.setRow(mappedRow);
            rowContext.setTransformerId(transformerId);
            rowContext.setRunId(runId);
            rowContext.setCohortId(cohortId);

            Map<String, Object> result = execute(rowContext);
            applyRowResult(row, result, returnMapping);
        }
        return rows;
    }

    private void applyRowResult(Map<String, Object> row,
                                Map<String, Object> result,
                                Map<String, String> returnMapping) {
        if (result == null || returnMapping == null || returnMapping.isEmpty()) {
            return;
        }
        for (Map.Entry<String, String> entry : returnMapping.entrySet()) {
            String returnKey = entry.getKey();
            String targetColumn = entry.getValue();
            if (targetColumn != null) {
                row.put(targetColumn, result.get(returnKey));
            }
        }
    }
}
