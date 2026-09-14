package bio.cosy.feddb.local.api.importer.functions.managed;

import bio.cosy.feddb.local.api.importer.functions.FunctionExecutionMode;

import java.util.List;
import java.util.Map;

/**
 * Abstract base class for managed cell functions, so functions running on a single value level,
 * returning a single value.
 * Extending classes must override:
 * - methodName()
 * - description()
 * - parameters() (Hyperparameters, e.g. Float factor for a unit conversion function)
 * - execute(CellFunctionExecutionContext context)
 */
public abstract class AbstractManagedCellFunction extends AbstractManagedFunction implements ManagedCellFunction {

    @Override
    public final FunctionExecutionMode mode() {
        return FunctionExecutionMode.CELL;
    }

    @Override
    public List<Map<String, Object>> apply(List<Map<String, Object>> rows,
                                           String columns,
                                           Map<String, String> inputMapping,
                                           Map<String, String> returnMapping,
                                           Map<String, Object> hyperparams,
                                           String cohortId,
                                           Long transformerId,
                                           Long runId) {
        if (columns == null) {
            throw new IllegalArgumentException("Column is required for CELL functions");
        }
        List<String> columnList = columns.contains(",") ?
                List.of(columns.split(",")) :
                List.of(columns);
        for (Map<String, Object> row : rows) {
            for (String column : columnList) {
                Map<String, Object> resolvedArgs = resolveArgs(row, column, inputMapping, hyperparams);
                Object value = row.get(column);

                CellFunctionExecutionContext cellContext = new CellFunctionExecutionContext();
                cellContext.setValue(value);
                cellContext.setParams(resolvedArgs);
                cellContext.setCohortId(cohortId);
                cellContext.setTransformerId(transformerId);
                cellContext.setRunId(runId);
                Object result = execute(cellContext);
                row.put(column, result);
            }
        }
        return rows;
    }
}
