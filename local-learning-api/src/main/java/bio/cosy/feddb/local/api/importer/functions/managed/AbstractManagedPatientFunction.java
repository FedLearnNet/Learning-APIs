package bio.cosy.feddb.local.api.importer.functions.managed;

import bio.cosy.feddb.local.api.importer.functions.FunctionExecutionMode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

/**
 * Base class for functions that operate on all rows of one patient at once.
 *
 * <p>The ETL pipeline invokes managed functions after rows have already been
 * grouped by patient. Each row in {@link PatientFunctionExecutionContext#getRows()}
 * therefore represents the configured input mappings for one row of the same
 * patient. The keys in a result map are translated through {@code returnMapping},
 * exactly as for a managed row function.</p>
 *
 * <p>What an implementation returns decides which rows survive:</p>
 * <ul>
 *   <li><b>one result per input row</b> — every row is kept; setting a different
 *       {@link PatientFunctionExecutionContext#getRowOrder() row order} reorders
 *       them;</li>
 *   <li><b>fewer results than input rows</b> — only the rows named by the row
 *       order are kept, in that order. This is how a function filters rows out of
 *       a patient (e.g. dropping the event rows a connector never maps);</li>
 *   <li><b>an empty result list</b> — the complete patient is removed from the
 *       remaining transformation, mapping and load phases.</li>
 * </ul>
 */
public abstract class AbstractManagedPatientFunction extends AbstractManagedFunction
        implements ManagedPatientFunction {

    @Override
    public final FunctionExecutionMode mode() {
        return FunctionExecutionMode.PATIENT;
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
        if (rows == null || rows.isEmpty()) {
            return rows;
        }

        List<Map<String, Object>> mappedRows = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            Map<String, Object> mappedRow = new LinkedHashMap<>();
            if (inputMapping != null) {
                for (Map.Entry<String, String> entry : inputMapping.entrySet()) {
                    mappedRow.put(entry.getKey(), resolveRowValue(entry.getValue(), row, column));
                }
            }
            mappedRows.add(mappedRow);
        }

        PatientFunctionExecutionContext context = new PatientFunctionExecutionContext();
        context.setRows(mappedRows);
        context.setParams(hyperparams == null ? Map.of() : new LinkedHashMap<>(hyperparams));
        context.setRowOrder(IntStream.range(0, rows.size()).boxed().toList());
        context.setTransformerId(transformerId);
        context.setRunId(runId);
        context.setCohortId(cohortId);

        List<Map<String, Object>> results = execute(context);
        if (results == null) {
            throw new IllegalStateException(methodName() + " returned null instead of patient results");
        }
        if (results.isEmpty()) {
            return new ArrayList<>();
        }
        if (results.size() > rows.size()) {
            throw new IllegalStateException(
                    methodName() + " must not return more results than the patient has rows"
            );
        }

        List<Integer> rowOrder = context.getRowOrder();
        validateRowOrder(rowOrder, results.size(), rows.size());
        List<Map<String, Object>> selectedRows = new ArrayList<>(results.size());
        for (int i = 0; i < results.size(); i++) {
            Map<String, Object> row = rows.get(rowOrder.get(i));
            applyRowResult(row, results.get(i), returnMapping);
            selectedRows.add(row);
        }
        if (results.size() == rows.size() && isOriginalOrder(rowOrder)) {
            return rows;
        }
        return selectedRows;
    }

    /**
     * Resolves one input mapping for one row. A mapping that names a column the
     * row does not have yields {@code null} — never the column name itself.
     * <p>
     * This matters as soon as the connector merges several source tables: rows
     * only carry the columns of the table they came from, so a lookup for another
     * table's column must read as "absent for this row" rather than as data.
     * Literal {@code [VALUE]} arguments keep working, and hyperparameters remain
     * available through {@link PatientFunctionExecutionContext#getParams()}.
     */
    private Object resolveRowValue(String spec, Map<String, Object> row, String column) {
        if (spec == null) {
            return null;
        }
        if ("[VALUE]".equals(spec)) {
            return column == null ? null : row.get(column);
        }
        if (spec.startsWith("[VALUE]")) {
            return spec.substring("[VALUE]".length());
        }
        return row.get(spec);
    }

    /**
     * The row order says which source row each result belongs to, so it needs one
     * distinct, in-range index per result: a subset of the rows for a filtering
     * function, a permutation of all of them for every other one.
     */
    private void validateRowOrder(List<Integer> rowOrder, int resultCount, int rowCount) {
        if (rowOrder == null || rowOrder.size() != resultCount
                || rowOrder.stream().anyMatch(index -> index == null || index < 0 || index >= rowCount)
                || rowOrder.stream().distinct().count() != resultCount) {
            throw new IllegalStateException(methodName() + " returned an invalid patient row order");
        }
    }

    private boolean isOriginalOrder(List<Integer> rowOrder) {
        for (int index = 0; index < rowOrder.size(); index++) {
            if (rowOrder.get(index) != index) {
                return false;
            }
        }
        return true;
    }

    private void applyRowResult(Map<String, Object> row,
                                Map<String, Object> result,
                                Map<String, String> returnMapping) {
        if (result == null || returnMapping == null || returnMapping.isEmpty()) {
            return;
        }
        for (Map.Entry<String, String> entry : returnMapping.entrySet()) {
            String targetColumn = entry.getValue();
            if (targetColumn != null) {
                row.put(targetColumn, result.get(entry.getKey()));
            }
        }
    }
}
