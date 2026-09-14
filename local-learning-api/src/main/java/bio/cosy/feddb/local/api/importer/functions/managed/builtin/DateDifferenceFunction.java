package bio.cosy.feddb.local.api.importer.functions.managed.builtin;

import bio.cosy.feddb.local.api.importer.functions.FunctionParameterDTO;
import bio.cosy.feddb.local.api.importer.functions.FunctionParameterType;
import bio.cosy.feddb.local.api.importer.functions.FunctionParameterUsage;
import bio.cosy.feddb.local.api.importer.functions.managed.AbstractManagedRowFunction;
import bio.cosy.feddb.local.api.importer.functions.managed.RowFunctionExecutionContext;
import jakarta.enterprise.context.ApplicationScoped;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Derives a duration from two timestamp columns — the length of stay a shared
 * schema stores as a single number, which relational sources such as MIMIC-IV
 * only carry implicitly as an admission and a discharge time.
 * <p>
 * Wiring: map {@code start} and {@code end} to the two source columns, set the
 * unit and the source {@code date_format}, and write the result back through
 * {@code returnMapping: {"value": "<target column>"}}. Rows where either side is
 * blank or unparseable yield {@code null}, which the mapping stage skips.
 */
@ApplicationScoped
public class DateDifferenceFunction extends AbstractManagedRowFunction {

    private static final String DEFAULT_DATE_FORMAT = "%Y-%m-%d %H:%M:%S";

    @Override
    public String methodName() {
        return "Date Difference";
    }

    @Override
    public String description() {
        return "Compute the difference between two date columns in days, hours, minutes or seconds.";
    }

    @Override
    public List<FunctionParameterDTO> parameters() {
        return List.of(
                new FunctionParameterDTO(
                        "start", FunctionParameterType.OBJECT, true, null,
                        "Start timestamp; map this parameter to the source column.", null,
                        FunctionParameterUsage.INPUT),
                new FunctionParameterDTO(
                        "end", FunctionParameterType.OBJECT, true, null,
                        "End timestamp; map this parameter to the source column.", null,
                        FunctionParameterUsage.INPUT),
                new FunctionParameterDTO(
                        "unit", FunctionParameterType.STRING, false, "DAYS",
                        "Unit of the result.", List.of("DAYS", "HOURS", "MINUTES", "SECONDS"),
                        FunctionParameterUsage.HYPERPARAMETER),
                new FunctionParameterDTO(
                        "date_format", FunctionParameterType.STRING, false, DEFAULT_DATE_FORMAT,
                        "Format of both source columns, accepting Java or Python-style directives.", null,
                        FunctionParameterUsage.HYPERPARAMETER),
                new FunctionParameterDTO(
                        "decimals", FunctionParameterType.INTEGER, false, "0",
                        "Fractional digits to keep (0 yields a whole number).", null,
                        FunctionParameterUsage.HYPERPARAMETER),
                new FunctionParameterDTO(
                        "absolute", FunctionParameterType.BOOLEAN, false, "false",
                        "Whether to return the absolute difference instead of a signed one.", null,
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
        DateTimeFormatter formatter = BuiltInFunctionSupport.resolveFormatter(
                params.getOrDefault("date_format", DEFAULT_DATE_FORMAT), DEFAULT_DATE_FORMAT);

        LocalDateTime start = BuiltInFunctionSupport.toDateTime(context.getRow().get("start"), formatter);
        LocalDateTime end = BuiltInFunctionSupport.toDateTime(context.getRow().get("end"), formatter);

        Map<String, Object> result = new HashMap<>();
        if (start == null || end == null) {
            result.put("value", null);
            return result;
        }

        String unit = String.valueOf(params.getOrDefault("unit", "DAYS")).strip().toUpperCase(Locale.ROOT);
        long seconds = ChronoUnit.SECONDS.between(start, end);
        boolean absolute = Boolean.parseBoolean(String.valueOf(params.getOrDefault("absolute", false)));
        if (absolute) {
            seconds = Math.abs(seconds);
        }

        int decimals = (int) Math.max(0, orDefault(BuiltInFunctionSupport.toDouble(params.get("decimals")), 0d));
        BigDecimal difference = BigDecimal.valueOf(seconds)
                .divide(BigDecimal.valueOf(secondsPerUnit(unit)), Math.max(decimals, 10), RoundingMode.HALF_UP)
                .setScale(decimals, RoundingMode.HALF_UP);

        result.put("value", decimals == 0
                ? String.valueOf(difference.longValueExact())
                : difference.toPlainString());
        return result;
    }

    private long secondsPerUnit(String unit) {
        return switch (unit) {
            case "SECONDS" -> 1L;
            case "MINUTES" -> 60L;
            case "HOURS" -> 3600L;
            case "DAYS" -> 86_400L;
            default -> throw new IllegalArgumentException("Unsupported 'unit': " + unit);
        };
    }

    private double orDefault(Double value, double fallback) {
        return value == null ? fallback : value;
    }
}
