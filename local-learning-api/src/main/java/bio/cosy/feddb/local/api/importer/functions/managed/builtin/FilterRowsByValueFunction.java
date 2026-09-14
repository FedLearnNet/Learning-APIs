package bio.cosy.feddb.local.api.importer.functions.managed.builtin;

import bio.cosy.feddb.local.api.importer.functions.FunctionParameterDTO;
import bio.cosy.feddb.local.api.importer.functions.FunctionParameterType;
import bio.cosy.feddb.local.api.importer.functions.FunctionParameterUsage;
import bio.cosy.feddb.local.api.importer.functions.managed.AbstractManagedPatientFunction;
import bio.cosy.feddb.local.api.importer.functions.managed.PatientFunctionExecutionContext;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Drops the source rows a connector has no use for, keeping the ones it does.
 * <p>
 * Event tables dwarf everything else in a clinical source — a MIMIC-IV patient
 * brings thousands of {@code labevents} rows of which two or three carry the
 * values the shared schema asks for. Every one of the others is carried through
 * transformation, mapping and load to produce nothing. Filtering them out once,
 * early in the chain, is the difference between an import that scales and one
 * that does not.
 * <p>
 * Rows are tested on the mapped {@code value}: with {@code mode=KEEP} only
 * matching rows survive, with {@code mode=DROP} matching rows are removed. Rows
 * that do not carry the column at all are governed by {@code on_missing}, which
 * defaults to {@code KEEP} — in a merged multi-table source those are the rows of
 * every <em>other</em> table, and a filter on lab item ids must not delete the
 * patient's admissions.
 * <p>
 * Two ordering rules matter when configuring this:
 * <ul>
 *   <li>put it <em>after</em> anything that counts the rows it removes (a
 *       "number of lab procedures" aggregate must see the full set);</li>
 *   <li>put it <em>before</em> the steps that read the rows it keeps, so they do
 *       less work.</li>
 * </ul>
 * If a patient ends up with no rows at all, that patient is dropped from the
 * import — the same semantics as {@code Filter Patient By Hit}.
 */
@ApplicationScoped
public class FilterRowsByValueFunction extends AbstractManagedPatientFunction {

    @Override
    public String methodName() {
        return "Filter Rows By Value";
    }

    @Override
    public String description() {
        return "Keep or drop a patient's source rows by the value of one column, leaving rows without that column "
                + "untouched.";
    }

    @Override
    public List<FunctionParameterDTO> parameters() {
        return List.of(
                new FunctionParameterDTO(
                        "value", FunctionParameterType.OBJECT, true, null,
                        "Column the rows are filtered on; map this parameter to the source column.", null,
                        FunctionParameterUsage.INPUT),
                new FunctionParameterDTO(
                        "values", FunctionParameterType.MAP, true, null,
                        "JSON array of values to match, e.g. [\"50852\", \"50931\"].", null,
                        FunctionParameterUsage.HYPERPARAMETER),
                new FunctionParameterDTO(
                        "mode", FunctionParameterType.STRING, false, "KEEP",
                        "Whether matching rows are the ones kept or the ones removed.",
                        List.of("KEEP", "DROP"),
                        FunctionParameterUsage.HYPERPARAMETER),
                new FunctionParameterDTO(
                        "match_mode", FunctionParameterType.STRING, false, "EQUALS",
                        "How the values are compared.",
                        List.of("EQUALS", "CONTAINS"),
                        FunctionParameterUsage.HYPERPARAMETER),
                new FunctionParameterDTO(
                        "on_missing", FunctionParameterType.STRING, false, "KEEP",
                        "What happens to rows that do not carry the column at all — in a merged multi-table source "
                                + "these are the rows of the other tables.",
                        List.of("KEEP", "DROP"),
                        FunctionParameterUsage.HYPERPARAMETER)
        );
    }

    @Override
    public List<Map<String, Object>> execute(PatientFunctionExecutionContext context) {
        Map<String, Object> params = context.getParams();
        List<String> values = BuiltInFunctionSupport.readStringList(params.get("values"), "values");
        if (values.isEmpty()) {
            throw new IllegalArgumentException("'values' must list at least one value");
        }
        boolean contains = "CONTAINS".equalsIgnoreCase(String.valueOf(params.getOrDefault("match_mode", "EQUALS")));
        boolean keepMatches = !"DROP".equalsIgnoreCase(String.valueOf(params.getOrDefault("mode", "KEEP")));
        boolean keepMissing = !"DROP".equalsIgnoreCase(String.valueOf(params.getOrDefault("on_missing", "KEEP")));

        List<Integer> kept = new ArrayList<>();
        for (int index = 0; index < context.getRows().size(); index++) {
            Object value = context.getRows().get(index).get("value");
            boolean keep = BuiltInFunctionSupport.isBlank(value)
                    ? keepMissing
                    : BuiltInFunctionSupport.matchesAny(value, values, contains) == keepMatches;
            if (keep) {
                kept.add(index);
            }
        }

        context.setRowOrder(kept);
        return new ArrayList<>(Collections.nCopies(kept.size(), Map.of()));
    }
}
