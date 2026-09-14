package bio.cosy.feddb.local.api.importer.functions.managed.builtin;

import bio.cosy.feddb.local.api.importer.functions.FunctionParameterDTO;
import bio.cosy.feddb.local.api.importer.functions.FunctionParameterType;
import bio.cosy.feddb.local.api.importer.functions.FunctionParameterUsage;
import bio.cosy.feddb.local.api.importer.functions.managed.AbstractManagedPatientFunction;
import bio.cosy.feddb.local.api.importer.functions.managed.PatientFunctionExecutionContext;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Labels every encounter of a patient with the categorical readmission outcome
 * the shared diabetes schema declares — {@code <30}, {@code >30} or {@code NO} —
 * from the encounter timestamps a source such as MIMIC-IV's {@code admissions}
 * table carries.
 * <p>
 * Encounters are ordered by {@code admission_date}; for each one the gap between
 * its {@code discharge_date} (falling back to the admission date) and the
 * <em>next</em> admission decides the label: within {@code days} yields
 * {@code within_label}, a later readmission {@code after_label}, and no further
 * admission {@code none_label}. The label is written onto the encounter's own
 * row, so with the encounter id mapped as the visit id every stay keeps its own
 * outcome.
 * <p>
 * This is the categorical sibling of {@code Readmission Target}, which produces
 * the binary 0/1 learning target from a single date column.
 */
@ApplicationScoped
public class ReadmissionIntervalClassFunction extends AbstractManagedPatientFunction {

    private static final String DEFAULT_DATE_FORMAT = "%Y-%m-%d %H:%M:%S";

    @Override
    public String methodName() {
        return "Readmission Interval Class";
    }

    @Override
    public String description() {
        return "Label each encounter of a patient as readmitted within N days, later, or not at all.";
    }

    @Override
    public List<FunctionParameterDTO> parameters() {
        return List.of(
                new FunctionParameterDTO(
                        "admission_date", FunctionParameterType.OBJECT, true, null,
                        "Admission timestamp; map this parameter to the source column.", null,
                        FunctionParameterUsage.INPUT),
                new FunctionParameterDTO(
                        "discharge_date", FunctionParameterType.OBJECT, false, null,
                        "Discharge timestamp; defaults to the admission timestamp when unmapped or blank.", null,
                        FunctionParameterUsage.INPUT),
                new FunctionParameterDTO(
                        "days", FunctionParameterType.INTEGER, false, "30",
                        "Gap in days up to which a readmission counts as early.", null,
                        FunctionParameterUsage.HYPERPARAMETER),
                new FunctionParameterDTO(
                        "date_format", FunctionParameterType.STRING, false, DEFAULT_DATE_FORMAT,
                        "Format of the timestamp columns, accepting Java or Python-style directives.", null,
                        FunctionParameterUsage.HYPERPARAMETER),
                new FunctionParameterDTO(
                        "within_label", FunctionParameterType.STRING, false, "<30",
                        "Label for a readmission within 'days'.", null,
                        FunctionParameterUsage.HYPERPARAMETER),
                new FunctionParameterDTO(
                        "after_label", FunctionParameterType.STRING, false, ">30",
                        "Label for a later readmission.", null,
                        FunctionParameterUsage.HYPERPARAMETER),
                new FunctionParameterDTO(
                        "none_label", FunctionParameterType.STRING, false, "NO",
                        "Label for an encounter without any following admission.", null,
                        FunctionParameterUsage.HYPERPARAMETER)
        );
    }

    @Override
    public List<String> returnKeys() {
        return List.of("value");
    }

    @Override
    public List<Map<String, Object>> execute(PatientFunctionExecutionContext context) {
        Map<String, Object> params = context.getParams();
        DateTimeFormatter formatter = BuiltInFunctionSupport.resolveFormatter(
                params.getOrDefault("date_format", DEFAULT_DATE_FORMAT), DEFAULT_DATE_FORMAT);
        long days = (long) Math.max(0, orDefault(BuiltInFunctionSupport.toDouble(params.get("days")), 30d));
        String withinLabel = String.valueOf(params.getOrDefault("within_label", "<30"));
        String afterLabel = String.valueOf(params.getOrDefault("after_label", ">30"));
        String noneLabel = String.valueOf(params.getOrDefault("none_label", "NO"));

        List<Encounter> encounters = new ArrayList<>();
        for (int index = 0; index < context.getRows().size(); index++) {
            Map<String, Object> row = context.getRows().get(index);
            LocalDateTime admission = BuiltInFunctionSupport.toDateTime(row.get("admission_date"), formatter);
            if (admission == null) {
                continue; // not an encounter row (or no usable timestamp)
            }
            LocalDateTime discharge = BuiltInFunctionSupport.toDateTime(row.get("discharge_date"), formatter);
            encounters.add(new Encounter(index, admission, discharge == null ? admission : discharge));
        }
        encounters.sort(Comparator.comparing(Encounter::admission).thenComparingInt(Encounter::index));

        List<Map<String, Object>> results = new ArrayList<>(context.getRows().size());
        for (int index = 0; index < context.getRows().size(); index++) {
            results.add(new HashMap<>());
        }

        for (int position = 0; position < encounters.size(); position++) {
            Encounter encounter = encounters.get(position);
            LocalDateTime nextAdmission = nextAdmissionAfter(encounters, position);
            String label;
            if (nextAdmission == null) {
                label = noneLabel;
            } else {
                long gap = ChronoUnit.DAYS.between(encounter.discharge(), nextAdmission);
                label = gap <= days ? withinLabel : afterLabel;
            }
            results.get(encounter.index()).put("value", label);
        }
        return results;
    }

    /** The first strictly later admission, so encounters sharing a timestamp do not readmit each other. */
    private LocalDateTime nextAdmissionAfter(List<Encounter> encounters, int position) {
        LocalDateTime current = encounters.get(position).admission();
        for (int next = position + 1; next < encounters.size(); next++) {
            LocalDateTime candidate = encounters.get(next).admission();
            if (candidate.isAfter(current)) {
                return candidate;
            }
        }
        return null;
    }

    private double orDefault(Double value, double fallback) {
        return value == null ? fallback : value;
    }

    private record Encounter(int index, LocalDateTime admission, LocalDateTime discharge) {
    }
}
