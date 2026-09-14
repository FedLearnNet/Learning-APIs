package bio.cosy.feddb.local.api.importer.functions.managed.builtin;

import bio.cosy.feddb.local.api.importer.functions.FunctionExecutionMode;
import bio.cosy.feddb.local.api.importer.functions.managed.AbstractManagedFunction;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class RemoveColumnsFunction extends AbstractManagedFunction {

    @Override
    public String methodName() {
        return "Remove Columns";
    }

    @Override
    public String description() {
        return "Remove columns from every row, such as the source columns another step has already "
                + "folded into one. Place it after the steps that read them.";
    }

    @Override
    public FunctionExecutionMode mode() {
        return FunctionExecutionMode.CELL;
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
        List<String> columns = columnsOf(column);
        if (columns.isEmpty()) {
            throw new IllegalArgumentException("Select at least one column to remove");
        }
        if (rows == null || rows.isEmpty()) {
            return rows;
        }

        List<String> removed = new ArrayList<>();
        for (String name : columns) {
            if (rows.getFirst().containsKey(name)) {
                removed.add(name);
            }
        }
        rows.forEach(row -> columns.forEach(row::remove));

        if (removed.size() < columns.size()) {
            List<String> missing = new ArrayList<>(columns);
            missing.removeAll(removed);
            // Worth saying out loud: a column named here that does not exist is almost always a
            // typo or a step that ran before the one producing it.
            logInfo("Remove Columns: no column named " + String.join(", ", missing) + " to remove",
                    transformerId, runId);
        }
        return rows;
    }

    private static List<String> columnsOf(String column) {
        if (column == null || column.isBlank()) {
            return List.of();
        }
        return Arrays.stream(column.split(","))
                .map(String::trim)
                .filter(name -> !name.isEmpty())
                .toList();
    }
}
