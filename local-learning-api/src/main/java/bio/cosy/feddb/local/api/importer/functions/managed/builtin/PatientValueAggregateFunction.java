package bio.cosy.feddb.local.api.importer.functions.managed.builtin;

import bio.cosy.feddb.local.api.importer.functions.FunctionParameterDTO;
import bio.cosy.feddb.local.api.importer.functions.FunctionParameterType;
import bio.cosy.feddb.local.api.importer.functions.FunctionParameterUsage;
import bio.cosy.feddb.local.api.importer.functions.managed.PatientFunctionExecutionContext;
import jakarta.enterprise.context.ApplicationScoped;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Condenses a long, event-shaped source table into the single value a shared
 * schema node expects: "the highest HbA1c of this admission", "the last serum
 * glucose of this patient", "how many lab results this encounter has".
 * <p>
 * The rows of one patient are filtered by {@code key} against the configured
 * {@code codes} (an item id, a loinc code, a test name), the remaining
 * {@code value}s are aggregated, and the result is written back onto the rows
 * selected by {@code write} — see {@link AbstractPatientDerivedValueFunction}.
 * With {@code write=GROUP} the aggregate is computed per {@code group} (e.g. per
 * encounter) and placed on the rows carrying the matching {@code target_group}.
 * <p>
 * The function is deliberately unaware of any particular vocabulary: which codes
 * mean HbA1c, and how the resulting number becomes a category, stay connector
 * configuration (typically followed by a {@code Numeric Threshold Class} step).
 */
@ApplicationScoped
public class PatientValueAggregateFunction extends AbstractPatientDerivedValueFunction {

    @Override
    public String methodName() {
        return "Patient Value Aggregate";
    }

    @Override
    public String description() {
        return "Aggregate a patient's long-format event rows (optionally filtered by a code column and grouped by "
                + "encounter) into one derived value per patient or encounter.";
    }

    @Override
    public List<FunctionParameterDTO> parameters() {
        List<FunctionParameterDTO> parameters = new ArrayList<>(List.of(
                new FunctionParameterDTO(
                        "value", FunctionParameterType.OBJECT, true, null,
                        "Measured value; map this parameter to the source column.", null,
                        FunctionParameterUsage.INPUT),
                new FunctionParameterDTO(
                        "key", FunctionParameterType.OBJECT, false, null,
                        "Code/name column the row is filtered on (omit to aggregate every non-blank value).", null,
                        FunctionParameterUsage.INPUT),
                new FunctionParameterDTO(
                        "order", FunctionParameterType.OBJECT, false, null,
                        "Column defining the order used by FIRST/LAST (defaults to the source row order).", null,
                        FunctionParameterUsage.INPUT),
                new FunctionParameterDTO(
                        "codes", FunctionParameterType.MAP, false, null,
                        "JSON array of accepted key values, e.g. [\"50852\"].", null,
                        FunctionParameterUsage.HYPERPARAMETER),
                new FunctionParameterDTO(
                        "match_mode", FunctionParameterType.STRING, false, "EQUALS",
                        "How 'codes' are compared against the key column.",
                        List.of("EQUALS", "CONTAINS"),
                        FunctionParameterUsage.HYPERPARAMETER),
                new FunctionParameterDTO(
                        "aggregate", FunctionParameterType.STRING, false, "MAX",
                        "Aggregation applied to the matching values.",
                        List.of("MAX", "MIN", "FIRST", "LAST", "MEAN", "SUM", "COUNT", "COUNT_DISTINCT"),
                        FunctionParameterUsage.HYPERPARAMETER),
                new FunctionParameterDTO(
                        "decimals", FunctionParameterType.INTEGER, false, "2",
                        "Fractional digits kept for numeric aggregates.", null,
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
        List<String> codes = BuiltInFunctionSupport.readStringList(params.get("codes"), "codes");
        boolean contains = "CONTAINS".equalsIgnoreCase(String.valueOf(params.getOrDefault("match_mode", "EQUALS")));
        String aggregate = String.valueOf(params.getOrDefault("aggregate", "MAX")).strip().toUpperCase(Locale.ROOT);
        int decimals = (int) Math.max(0, orDefault(BuiltInFunctionSupport.toDouble(params.get("decimals")), 2d));

        List<Match> matches = new ArrayList<>();
        for (int index = 0; index < context.getRows().size(); index++) {
            Map<String, Object> row = context.getRows().get(index);
            boolean keyMatches = !row.containsKey("key")
                    || BuiltInFunctionSupport.matchesAny(row.get("key"), codes, contains);
            if (!keyMatches || BuiltInFunctionSupport.isBlank(row.get(VALUE_KEY))) {
                continue;
            }
            matches.add(new Match(index, sourceGroup(row), row.get(VALUE_KEY), row.get("order")));
        }

        Map<String, Object> valueByGroup = new LinkedHashMap<>();
        Map<String, List<Match>> byGroup = new LinkedHashMap<>();
        for (Match match : matches) {
            if (match.group() != null) {
                byGroup.computeIfAbsent(match.group(), ignored -> new ArrayList<>()).add(match);
            }
        }
        for (Map.Entry<String, List<Match>> entry : byGroup.entrySet()) {
            Object value = aggregate(entry.getValue(), aggregate, decimals);
            if (value != null) {
                valueByGroup.put(entry.getKey(), value);
            }
        }

        return emit(context, aggregate(matches, aggregate, decimals), valueByGroup);
    }

    private Object aggregate(List<Match> matches, String aggregate, int decimals) {
        if (matches.isEmpty()) {
            return aggregate.startsWith("COUNT") ? "0" : null;
        }

        switch (aggregate) {
            case "COUNT":
                return String.valueOf(matches.size());
            case "COUNT_DISTINCT": {
                Set<String> distinct = new LinkedHashSet<>();
                for (Match match : matches) {
                    distinct.add(BuiltInFunctionSupport.normalizeCodeKey(match.value()).strip());
                }
                return String.valueOf(distinct.size());
            }
            case "FIRST":
                return ordered(matches).getFirst().value();
            case "LAST":
                return ordered(matches).getLast().value();
            default:
                break;
        }

        List<Double> numbers = new ArrayList<>(matches.size());
        for (Match match : matches) {
            Double number = BuiltInFunctionSupport.toDouble(match.value());
            if (number != null) {
                numbers.add(number);
            }
        }
        if (numbers.isEmpty()) {
            return null;
        }

        double result = switch (aggregate) {
            case "MIN" -> numbers.stream().mapToDouble(Double::doubleValue).min().orElseThrow();
            case "SUM" -> numbers.stream().mapToDouble(Double::doubleValue).sum();
            case "MEAN" -> numbers.stream().mapToDouble(Double::doubleValue).average().orElseThrow();
            case "MAX" -> numbers.stream().mapToDouble(Double::doubleValue).max().orElseThrow();
            default -> throw new IllegalArgumentException("Unsupported 'aggregate': " + aggregate);
        };
        return format(result, decimals);
    }

    /** Sorts by the optional order column, keeping the source order for equal or missing keys. */
    private List<Match> ordered(List<Match> matches) {
        List<Match> sorted = new ArrayList<>(matches);
        sorted.sort(Comparator
                .comparing((Match match) -> BuiltInFunctionSupport.isBlank(match.order())
                        ? null
                        : BuiltInFunctionSupport.normalizeCodeKey(match.order()).strip(),
                        Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparingInt(Match::index));
        return sorted;
    }

    private String format(double value, int decimals) {
        BigDecimal decimal = BigDecimal.valueOf(value).setScale(decimals, RoundingMode.HALF_UP).stripTrailingZeros();
        return decimal.scale() <= 0 ? decimal.toBigInteger().toString() : decimal.toPlainString();
    }

    private double orDefault(Double value, double fallback) {
        return value == null ? fallback : value;
    }

    private record Match(int index, String group, Object value, Object order) {
    }
}
