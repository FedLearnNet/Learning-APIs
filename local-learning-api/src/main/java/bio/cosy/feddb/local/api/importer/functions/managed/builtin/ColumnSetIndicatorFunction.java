package bio.cosy.feddb.local.api.importer.functions.managed.builtin;

import bio.cosy.feddb.local.api.importer.functions.FunctionParameterDTO;
import bio.cosy.feddb.local.api.importer.functions.FunctionParameterType;
import bio.cosy.feddb.local.api.importer.functions.FunctionParameterUsage;
import bio.cosy.feddb.local.api.importer.functions.managed.AbstractManagedRowFunction;
import bio.cosy.feddb.local.api.importer.functions.managed.RowFunctionExecutionContext;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Collapses a set of sibling columns into one indicator — the shape behind
 * summary variables such as "was any diabetes medication prescribed" or "was any
 * dosage changed", which a shared schema stores as a single category while the
 * evidence sits in many per-agent columns.
 * <p>
 * Every column mapped through {@code inputMapping} is inspected (the parameter
 * names are free, e.g. {@code m1}…{@code m23}). Blank cells are ignored. A cell
 * counts as a hit when its value is in {@code values} ({@code comparison=IN}) or
 * not in it ({@code comparison=NOT_IN}); {@code mode} decides whether one hit
 * ({@code ANY}) or only an all-hit row ({@code ALL}) yields {@code match_label},
 * with {@code default_label} otherwise.
 * <p>
 * Rows in which <em>every</em> mapped column is blank produce {@code null}
 * instead of {@code default_label}. In a merged multi-table source only the rows
 * that actually carry the sibling columns then receive the indicator, which is
 * what keeps an atomic schema node from being fed one value per source row.
 */
@ApplicationScoped
public class ColumnSetIndicatorFunction extends AbstractManagedRowFunction {

    @Override
    public String methodName() {
        return "Column Set Indicator";
    }

    @Override
    public String description() {
        return "Emit one indicator label from a set of columns (e.g. 'any of these medications was changed').";
    }

    @Override
    public List<FunctionParameterDTO> parameters() {
        return List.of(
                new FunctionParameterDTO(
                        "values", FunctionParameterType.MAP, true, null,
                        "JSON array of values that count as a hit, e.g. [\"Up\", \"Down\"].", null,
                        FunctionParameterUsage.HYPERPARAMETER),
                new FunctionParameterDTO(
                        "comparison", FunctionParameterType.STRING, false, "IN",
                        "Whether a cell hits when it is in 'values' or when it is not.",
                        List.of("IN", "NOT_IN"),
                        FunctionParameterUsage.HYPERPARAMETER),
                new FunctionParameterDTO(
                        "mode", FunctionParameterType.STRING, false, "ANY",
                        "Whether one hit is enough or every non-blank column must hit.",
                        List.of("ANY", "ALL"),
                        FunctionParameterUsage.HYPERPARAMETER),
                new FunctionParameterDTO(
                        "match_label", FunctionParameterType.STRING, true, null,
                        "Value written when the row matches.", null,
                        FunctionParameterUsage.HYPERPARAMETER),
                new FunctionParameterDTO(
                        "default_label", FunctionParameterType.STRING, true, null,
                        "Value written when the row does not match.", null,
                        FunctionParameterUsage.HYPERPARAMETER)
        );
    }

    @Override
    public List<String> returnKeys() {
        return List.of("value");
    }

    @Override
    public Map<String, Object> execute(RowFunctionExecutionContext context) {
        Map<String, Object> params = context.getParams();
        List<String> values = BuiltInFunctionSupport.readStringList(params.get("values"), "values");
        boolean notIn = "NOT_IN".equalsIgnoreCase(String.valueOf(params.getOrDefault("comparison", "IN")));
        boolean all = "ALL".equalsIgnoreCase(String.valueOf(params.getOrDefault("mode", "ANY")));

        int inspected = 0;
        int hits = 0;
        for (Object cell : context.getRow().values()) {
            if (BuiltInFunctionSupport.isBlank(cell)) {
                continue;
            }
            inspected++;
            boolean inSet = BuiltInFunctionSupport.matchesAny(cell, values, false);
            if (notIn != inSet) {
                hits++;
            }
        }

        Map<String, Object> result = new HashMap<>();
        if (inspected == 0) {
            result.put("value", null); // no evidence on this row — leave it untouched
            return result;
        }

        boolean matched = all ? hits == inspected : hits > 0;
        result.put("value", String.valueOf(params.get(matched ? "match_label" : "default_label")));
        return result;
    }
}
