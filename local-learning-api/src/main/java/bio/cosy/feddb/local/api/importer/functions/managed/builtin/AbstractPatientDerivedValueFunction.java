package bio.cosy.feddb.local.api.importer.functions.managed.builtin;

import bio.cosy.feddb.local.api.importer.functions.FunctionParameterDTO;
import bio.cosy.feddb.local.api.importer.functions.FunctionParameterType;
import bio.cosy.feddb.local.api.importer.functions.FunctionParameterUsage;
import bio.cosy.feddb.local.api.importer.functions.managed.AbstractManagedPatientFunction;
import bio.cosy.feddb.local.api.importer.functions.managed.PatientFunctionExecutionContext;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Base class for patient-mode built-ins that condense many source rows into one
 * derived value per patient (or per encounter) and write it back onto the rows.
 * <p>
 * Sources that arrive as several long tables merged by patient id — MIMIC-IV's
 * {@code labevents}, {@code prescriptions}, … — need exactly this shape: the
 * evidence lives on hundreds of rows, the shared schema wants a single value.
 * <p>
 * <b>Where the value is written matters.</b> The load stage rejects an
 * {@code ATOMIC_ATTRIBUTE} that receives more than one value for the same
 * visit, so broadcasting a derived value onto every row of the patient would
 * make the importer discard it. {@code write} therefore selects the target rows:
 * <ul>
 *   <li>{@code GROUP} — write onto the rows carrying the matching
 *       {@code target_group} value (e.g. the admission row of that encounter),
 *       which is also what makes the value land on the right visit id;</li>
 *   <li>{@code ANCHOR} — write onto the first row with a non-blank
 *       {@code anchor} (e.g. a column that exists once per patient);</li>
 *   <li>{@code FIRST} — write onto the first row of the patient;</li>
 *   <li>{@code ALL} — write onto every row (only safe for list attributes).</li>
 * </ul>
 * All other rows receive {@code null}, which the mapping stage skips.
 */
public abstract class AbstractPatientDerivedValueFunction extends AbstractManagedPatientFunction {

    protected static final String VALUE_KEY = "value";

    /** Row-placement and grouping parameters shared by every derived-value function. */
    protected static List<FunctionParameterDTO> placementParameters() {
        return List.of(
                new FunctionParameterDTO(
                        "group", FunctionParameterType.OBJECT, false, null,
                        "Group id on the source rows (e.g. the encounter id of a lab result).", null,
                        FunctionParameterUsage.INPUT),
                new FunctionParameterDTO(
                        "target_group", FunctionParameterType.OBJECT, false, null,
                        "Group id on the rows that should receive the derived value (write=GROUP).", null,
                        FunctionParameterUsage.INPUT),
                new FunctionParameterDTO(
                        "anchor", FunctionParameterType.OBJECT, false, null,
                        "Marks the row that should receive the derived value (write=ANCHOR).", null,
                        FunctionParameterUsage.INPUT),
                new FunctionParameterDTO(
                        "write", FunctionParameterType.STRING, false, "ANCHOR",
                        "Which rows receive the derived value.",
                        List.of("GROUP", "ANCHOR", "FIRST", "ALL"),
                        FunctionParameterUsage.HYPERPARAMETER),
                new FunctionParameterDTO(
                        "default", FunctionParameterType.STRING, false, "",
                        "Value written when the patient (or encounter) has no matching source row.", null,
                        FunctionParameterUsage.HYPERPARAMETER)
        );
    }

    /**
     * Places {@code overall} (or, for {@code write=GROUP}, the per-group values)
     * on the rows selected by the configured write mode and returns one result
     * map per input row.
     */
    protected List<Map<String, Object>> emit(PatientFunctionExecutionContext context,
                                             Object overall,
                                             Map<String, Object> valueByGroup) {
        List<Map<String, Object>> rows = context.getRows();
        Map<String, Object> params = context.getParams();
        String write = String.valueOf(params.getOrDefault("write", "ANCHOR")).strip().toUpperCase(Locale.ROOT);
        Object fallback = params.get("default");

        List<Map<String, Object>> results = new ArrayList<>(rows.size());
        for (int index = 0; index < rows.size(); index++) {
            results.add(new HashMap<>());
        }

        switch (write) {
            case "GROUP" -> {
                for (int index = 0; index < rows.size(); index++) {
                    Object target = rows.get(index).get("target_group");
                    if (BuiltInFunctionSupport.isBlank(target)) {
                        continue;
                    }
                    String key = BuiltInFunctionSupport.normalizeCodeKey(target).strip();
                    Object value = valueByGroup == null ? null : valueByGroup.get(key);
                    results.get(index).put(VALUE_KEY, value == null ? fallback : value);
                }
            }
            case "ALL" -> {
                for (Map<String, Object> result : results) {
                    result.put(VALUE_KEY, overall == null ? fallback : overall);
                }
            }
            default -> {
                int target = "FIRST".equals(write) ? 0 : anchorIndex(rows);
                if (target >= 0 && target < results.size()) {
                    results.get(target).put(VALUE_KEY, overall == null ? fallback : overall);
                }
            }
        }
        return results;
    }

    /** First row carrying a non-blank {@code anchor}, falling back to the first row. */
    private int anchorIndex(List<Map<String, Object>> rows) {
        for (int index = 0; index < rows.size(); index++) {
            if (!BuiltInFunctionSupport.isBlank(rows.get(index).get("anchor"))) {
                return index;
            }
        }
        return rows.isEmpty() ? -1 : 0;
    }

    /** Group key of a source row, or {@code null} when the row carries none. */
    protected String sourceGroup(Map<String, Object> row) {
        Object group = row.get("group");
        return BuiltInFunctionSupport.isBlank(group)
                ? null
                : BuiltInFunctionSupport.normalizeCodeKey(group).strip();
    }
}
