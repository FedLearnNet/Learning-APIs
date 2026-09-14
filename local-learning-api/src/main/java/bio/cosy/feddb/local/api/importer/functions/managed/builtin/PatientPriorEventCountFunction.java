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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Counts, for each of a patient's dated events, how many earlier events of the
 * same patient fall inside a time window before it.
 * <p>
 * This is the shape of the "history" variables a shared schema carries per
 * encounter — the number of outpatient, emergency or inpatient contacts in the
 * year preceding an admission. A source that simply lists admissions holds that
 * information implicitly; the count only exists once the encounters are read
 * relative to each other.
 * <p>
 * Events are the rows carrying a non-blank {@code date}. With {@code codes} set,
 * only events whose {@code key} matches are counted, which is how one admission
 * table yields three different history counts. The count is written onto the
 * event's own row, so with the encounter id mapped as the visit id every stay
 * keeps its own history.
 */
@ApplicationScoped
public class PatientPriorEventCountFunction extends AbstractManagedPatientFunction {

    private static final String DEFAULT_DATE_FORMAT = "%Y-%m-%d %H:%M:%S";

    @Override
    public String methodName() {
        return "Patient Prior Event Count";
    }

    @Override
    public String description() {
        return "Count a patient's earlier events within a time window before each event (e.g. admissions in the "
                + "preceding year).";
    }

    @Override
    public List<FunctionParameterDTO> parameters() {
        return List.of(
                new FunctionParameterDTO(
                        "date", FunctionParameterType.OBJECT, true, null,
                        "Event timestamp; map this parameter to the source column.", null,
                        FunctionParameterUsage.INPUT),
                new FunctionParameterDTO(
                        "key", FunctionParameterType.OBJECT, false, null,
                        "Category column deciding which events count (omit to count every event).", null,
                        FunctionParameterUsage.INPUT),
                new FunctionParameterDTO(
                        "codes", FunctionParameterType.MAP, false, null,
                        "JSON array of key values that count, e.g. [\"EW EMER.\", \"DIRECT EMER.\"].", null,
                        FunctionParameterUsage.HYPERPARAMETER),
                new FunctionParameterDTO(
                        "days", FunctionParameterType.INTEGER, false, "365",
                        "Length of the look-back window in days.", null,
                        FunctionParameterUsage.HYPERPARAMETER),
                new FunctionParameterDTO(
                        "date_format", FunctionParameterType.STRING, false, DEFAULT_DATE_FORMAT,
                        "Format of the timestamp column, accepting Java or Python-style directives.", null,
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
        long days = (long) Math.max(0, orDefault(BuiltInFunctionSupport.toDouble(params.get("days")), 365d));
        List<String> codes = BuiltInFunctionSupport.readStringList(params.get("codes"), "codes");

        List<Event> events = new ArrayList<>();
        for (int index = 0; index < context.getRows().size(); index++) {
            Map<String, Object> row = context.getRows().get(index);
            LocalDateTime date = BuiltInFunctionSupport.toDateTime(row.get("date"), formatter);
            if (date == null) {
                continue; // not an event row (or no usable timestamp)
            }
            boolean counts = !row.containsKey("key") || BuiltInFunctionSupport.matchesAny(row.get("key"), codes, false);
            events.add(new Event(index, date, counts));
        }

        List<Map<String, Object>> results = new ArrayList<>(context.getRows().size());
        for (int index = 0; index < context.getRows().size(); index++) {
            results.add(new HashMap<>());
        }

        for (Event event : events) {
            long count = 0;
            for (Event other : events) {
                if (other == event || !other.counts() || !other.date().isBefore(event.date())) {
                    continue;
                }
                if (ChronoUnit.DAYS.between(other.date(), event.date()) <= days) {
                    count++;
                }
            }
            results.get(event.index()).put("value", String.valueOf(count));
        }
        return results;
    }

    private double orDefault(Double value, double fallback) {
        return value == null ? fallback : value;
    }

    private record Event(int index, LocalDateTime date, boolean counts) {
    }
}
