package bio.cosy.feddb.local.api.importer.functions.managed.builtin;

import bio.cosy.feddb.local.api.importer.functions.FunctionParameterDTO;
import bio.cosy.feddb.local.api.importer.functions.FunctionParameterType;
import bio.cosy.feddb.local.api.importer.functions.FunctionParameterUsage;
import bio.cosy.feddb.local.api.importer.functions.managed.AbstractManagedPatientFunction;
import bio.cosy.feddb.local.api.importer.functions.managed.PatientFunctionExecutionContext;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;

/** Calculates a binary readmission target from the next dated row of one patient. */
@ApplicationScoped
public class ReadmissionTargetFunction extends AbstractManagedPatientFunction {

    private static final String DEFAULT_DATE_FORMAT = "%Y-%m-%d";

    @Override
    public String methodName() {
        return "Readmission Target";
    }

    @Override
    public String description() {
        return "Sort a patient's non-empty dates and set target=1 when the next dated row is within the configured number of days.";
    }

    @Override
    public List<FunctionParameterDTO> parameters() {
        return List.of(
                new FunctionParameterDTO(
                        "date", FunctionParameterType.OBJECT, true, null,
                        "Encounter date; map this parameter to the source date column.", null,
                        FunctionParameterUsage.INPUT),
                new FunctionParameterDTO(
                        "days", FunctionParameterType.INTEGER, true, "30",
                        "Maximum number of days until the next encounter counts as a readmission.", null,
                        FunctionParameterUsage.HYPERPARAMETER),
                new FunctionParameterDTO(
                        "date_format", FunctionParameterType.STRING, false, DEFAULT_DATE_FORMAT,
                        "Date format, accepting Java or Python-style directives.", null,
                        FunctionParameterUsage.HYPERPARAMETER)
        );
    }

    @Override
    public List<String> returnKeys() {
        return List.of("target");
    }

    @Override
    public List<Map<String, Object>> execute(PatientFunctionExecutionContext context) {
        int days = parseDays(context.getParams().getOrDefault("days", 30));
        DateTimeFormatter formatter = BuiltInFunctionSupport.resolveFormatter(
                context.getParams().getOrDefault("date_format", DEFAULT_DATE_FORMAT),
                DEFAULT_DATE_FORMAT
        );

        List<DatedRow> sortedRows = new ArrayList<>(context.getRows().size());
        for (int index = 0; index < context.getRows().size(); index++) {
            Object rawDate = context.getRows().get(index).get("date");
            sortedRows.add(new DatedRow(index, parseDate(rawDate, formatter)));
        }
        sortedRows.sort(Comparator.comparing(
                DatedRow::date,
                Comparator.nullsLast(Comparator.naturalOrder())
        ));

        int[] targets = new int[context.getRows().size()];
        for (int index = 0; index + 1 < sortedRows.size(); index++) {
            DatedRow current = sortedRows.get(index);
            DatedRow next = sortedRows.get(index + 1);
            if (current.date() == null || next.date() == null) {
                continue;
            }

            long difference = ChronoUnit.DAYS.between(current.date(), next.date());
            if (difference >= 0 && difference <= days) {
                targets[index] = 1;
            }
        }

        context.setRowOrder(sortedRows.stream().map(DatedRow::originalIndex).toList());
        List<Map<String, Object>> results = new ArrayList<>(targets.length);
        for (int target : targets) {
            results.add(Map.of("target", target));
        }
        return results;
    }

    private int parseDays(Object rawDays) {
        try {
            int days = rawDays instanceof Number number
                    ? number.intValue()
                    : Integer.parseInt(String.valueOf(rawDays).strip());
            if (days < 0) {
                throw new IllegalArgumentException("'days' must not be negative");
            }
            return days;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("'days' must be an integer", e);
        }
    }

    private LocalDate parseDate(Object rawDate, DateTimeFormatter formatter) {
        if (rawDate == null) {
            return null;
        }
        if (rawDate instanceof LocalDate date) {
            return date;
        }
        if (rawDate instanceof LocalDateTime dateTime) {
            return dateTime.toLocalDate();
        }
        if (rawDate instanceof OffsetDateTime dateTime) {
            return dateTime.toLocalDate();
        }
        if (rawDate instanceof Instant instant) {
            return instant.atZone(ZoneId.systemDefault()).toLocalDate();
        }
        if (rawDate instanceof Date date) {
            return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        }

        String text = String.valueOf(rawDate).strip();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return LocalDate.parse(text, formatter);
        } catch (DateTimeParseException ignored) {
            try {
                return LocalDateTime.parse(text, formatter).toLocalDate();
            } catch (DateTimeParseException ignoredAgain) {
                return null;
            }
        }
    }

    private record DatedRow(int originalIndex, LocalDate date) {
    }
}
