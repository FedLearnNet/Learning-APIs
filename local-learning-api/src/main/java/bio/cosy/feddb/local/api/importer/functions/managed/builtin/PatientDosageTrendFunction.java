package bio.cosy.feddb.local.api.importer.functions.managed.builtin;

import bio.cosy.feddb.local.api.importer.functions.FunctionParameterDTO;
import bio.cosy.feddb.local.api.importer.functions.FunctionParameterType;
import bio.cosy.feddb.local.api.importer.functions.FunctionParameterUsage;
import bio.cosy.feddb.local.api.importer.functions.managed.PatientFunctionExecutionContext;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Derives the four-level dosage-change category ({@code No}, {@code Steady},
 * {@code Up}, {@code Down}) that the shared diabetes schema stores per agent,
 * from an order-entry table that only records individual prescriptions.
 * <p>
 * The patient's rows are filtered by {@code name} against the {@code match}
 * tokens (substring, case-insensitive — so "Metformin" also catches
 * "MetFORMIN XR (Glucophage XR)"), ordered by {@code order}, and the first and
 * last numeric {@code dose} are compared: a higher last dose yields
 * {@code up_label}, a lower one {@code down_label}, otherwise
 * {@code steady_label}. A patient with prescriptions but no usable dose still
 * counts as prescribed and yields {@code steady_label}; a patient with none gets
 * the configured {@code default} (e.g. {@code No}).
 * <p>
 * Placement follows {@link AbstractPatientDerivedValueFunction}: with
 * {@code write=GROUP} the comparison runs per encounter and lands on that
 * encounter's row.
 */
@ApplicationScoped
public class PatientDosageTrendFunction extends AbstractPatientDerivedValueFunction {

    @Override
    public String methodName() {
        return "Patient Dosage Trend";
    }

    @Override
    public String description() {
        return "Derive a No/Steady/Up/Down dosage-change category for one agent from a patient's prescription rows.";
    }

    @Override
    public List<FunctionParameterDTO> parameters() {
        List<FunctionParameterDTO> parameters = new ArrayList<>(List.of(
                new FunctionParameterDTO(
                        "name", FunctionParameterType.OBJECT, true, null,
                        "Drug/agent name; map this parameter to the source column.", null,
                        FunctionParameterUsage.INPUT),
                new FunctionParameterDTO(
                        "dose", FunctionParameterType.OBJECT, false, null,
                        "Dose value; map this parameter to the source column (omit to always report 'steady').", null,
                        FunctionParameterUsage.INPUT),
                new FunctionParameterDTO(
                        "order", FunctionParameterType.OBJECT, false, null,
                        "Column defining the chronological order of the prescriptions.", null,
                        FunctionParameterUsage.INPUT),
                new FunctionParameterDTO(
                        "match", FunctionParameterType.MAP, true, null,
                        "JSON array of name fragments identifying the agent, e.g. [\"metformin\", \"glucophage\"].",
                        null, FunctionParameterUsage.HYPERPARAMETER),
                new FunctionParameterDTO(
                        "steady_label", FunctionParameterType.STRING, false, "Steady",
                        "Label when the dose does not change.", null,
                        FunctionParameterUsage.HYPERPARAMETER),
                new FunctionParameterDTO(
                        "up_label", FunctionParameterType.STRING, false, "Up",
                        "Label when the last dose is higher than the first.", null,
                        FunctionParameterUsage.HYPERPARAMETER),
                new FunctionParameterDTO(
                        "down_label", FunctionParameterType.STRING, false, "Down",
                        "Label when the last dose is lower than the first.", null,
                        FunctionParameterUsage.HYPERPARAMETER)
        ));
        parameters.addAll(placementParameters());
        return parameters;
    }

    @Override
    public List<String> returnKeys() {
        return List.of(VALUE_KEY);
    }

    @Override
    public List<Map<String, Object>> execute(PatientFunctionExecutionContext context) {
        Map<String, Object> params = context.getParams();
        List<String> match = BuiltInFunctionSupport.readStringList(params.get("match"), "match");
        if (match.isEmpty()) {
            throw new IllegalArgumentException("'match' must list at least one name fragment");
        }

        List<Prescription> prescriptions = new ArrayList<>();
        for (int index = 0; index < context.getRows().size(); index++) {
            Map<String, Object> row = context.getRows().get(index);
            if (!BuiltInFunctionSupport.matchesAny(row.get("name"), match, true)) {
                continue;
            }
            prescriptions.add(new Prescription(
                    index, sourceGroup(row), BuiltInFunctionSupport.toDouble(row.get("dose")), row.get("order")));
        }

        Map<String, List<Prescription>> byGroup = new LinkedHashMap<>();
        for (Prescription prescription : prescriptions) {
            if (prescription.group() != null) {
                byGroup.computeIfAbsent(prescription.group(), ignored -> new ArrayList<>()).add(prescription);
            }
        }
        Map<String, Object> valueByGroup = new LinkedHashMap<>();
        for (Map.Entry<String, List<Prescription>> entry : byGroup.entrySet()) {
            valueByGroup.put(entry.getKey(), trend(entry.getValue(), params));
        }

        return emit(context, prescriptions.isEmpty() ? null : trend(prescriptions, params), valueByGroup);
    }

    private Object trend(List<Prescription> prescriptions, Map<String, Object> params) {
        String steady = String.valueOf(params.getOrDefault("steady_label", "Steady"));
        if (prescriptions.isEmpty()) {
            return null;
        }

        List<Prescription> dosed = new ArrayList<>(prescriptions.stream()
                .filter(prescription -> prescription.dose() != null)
                .toList());
        if (dosed.isEmpty()) {
            return steady; // prescribed, but the source carries no comparable dose
        }
        dosed.sort(Comparator
                .comparing((Prescription prescription) -> BuiltInFunctionSupport.isBlank(prescription.order())
                        ? null
                        : BuiltInFunctionSupport.normalizeCodeKey(prescription.order()).strip(),
                        Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparingInt(Prescription::index));

        double first = dosed.getFirst().dose();
        double last = dosed.getLast().dose();
        if (last > first) {
            return String.valueOf(params.getOrDefault("up_label", "Up"));
        }
        if (last < first) {
            return String.valueOf(params.getOrDefault("down_label", "Down"));
        }
        return steady;
    }

    private record Prescription(int index, String group, Double dose, Object order) {
    }
}
