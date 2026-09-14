package bio.cosy.feddb.local.api.importer.functions.managed.builtin;

import bio.cosy.feddb.local.api.importer.functions.FunctionParameterDTO;
import bio.cosy.feddb.local.api.importer.functions.FunctionParameterType;
import bio.cosy.feddb.local.api.importer.functions.managed.AbstractManagedCellFunction;
import bio.cosy.feddb.local.api.importer.functions.managed.CellFunctionExecutionContext;
import jakarta.enterprise.context.ApplicationScoped;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Bins a continuous measurement into the half-open range labels a shared schema
 * declares as categories — {@code [70-80)}, {@code [175-200)} and so on. Sites
 * that export a raw number (age in years, weight in pounds, a lab value) are
 * harmonised onto a banded schema node through configuration alone.
 * <p>
 * The bands are {@code [min, min+group_size)}, {@code [min+group_size,
 * min+2*group_size)}, … up to {@code max}. Values at or above {@code max} become
 * {@code >max} (matching the trailing open band of the US-130/MIMIC weight node)
 * or, with {@code overflow=CLAMP}, fall into the last closed band; values below
 * {@code min} become {@code <min} or, when clamping, the first band. Blank and
 * non-numeric cells pass through unchanged so the schema layer stays responsible
 * for genuinely bad values.
 */
@ApplicationScoped
public class NumericRangeGroupFunction extends AbstractManagedCellFunction {

    private static final String OVERFLOW_CLAMP = "CLAMP";

    @Override
    public String methodName() {
        return "Numeric Range Group Mapper";
    }

    @Override
    public String description() {
        return "Bin a numeric value into fixed-width range labels such as [70-80) or >300.";
    }

    @Override
    public List<FunctionParameterDTO> parameters() {
        return List.of(
                FunctionParameterDTO.of("min", FunctionParameterType.DOUBLE, false, "0",
                        "Lower bound of the first band."),
                FunctionParameterDTO.of("max", FunctionParameterType.DOUBLE, true, null,
                        "Upper bound of the last band; values at or above it become '>max'."),
                FunctionParameterDTO.of("group_size", FunctionParameterType.DOUBLE, true, null,
                        "Width of one band."),
                FunctionParameterDTO.of("overflow", FunctionParameterType.STRING, false, "LABEL",
                        "LABEL emits '>max'/'<min' outside the range, CLAMP folds those values into the outermost band.")
        );
    }

    @Override
    public Object execute(CellFunctionExecutionContext context) {
        Object value = context.getValue();
        Double numeric = BuiltInFunctionSupport.toDouble(value);
        if (numeric == null) {
            return value; // blank or not numeric — leave for schema-level validation
        }

        Map<String, Object> params = context.getParams();
        double min = orDefault(BuiltInFunctionSupport.toDouble(params.get("min")), 0d);
        Double max = BuiltInFunctionSupport.toDouble(params.get("max"));
        Double groupSize = BuiltInFunctionSupport.toDouble(params.get("group_size"));
        if (max == null || groupSize == null || groupSize <= 0 || max <= min) {
            throw new IllegalArgumentException(
                    "'max' and 'group_size' are required, 'group_size' must be positive and 'max' greater than 'min'");
        }
        boolean clamp = OVERFLOW_CLAMP.equalsIgnoreCase(String.valueOf(params.getOrDefault("overflow", "LABEL")));

        if (numeric < min) {
            return clamp ? band(min, Math.min(min + groupSize, max)) : "<" + format(min);
        }
        if (numeric >= max) {
            return clamp ? band(Math.max(min, max - groupSize), max) : ">" + format(max);
        }

        long index = (long) Math.floor((numeric - min) / groupSize);
        double lower = min + index * groupSize;
        return band(lower, Math.min(lower + groupSize, max));
    }

    private String band(double lower, double upper) {
        return "[" + format(lower) + "-" + format(upper) + ")";
    }

    /** Renders band boundaries without a trailing {@code .0} so labels match the schema options. */
    private String format(double value) {
        BigDecimal decimal = BigDecimal.valueOf(value).stripTrailingZeros();
        return decimal.scale() <= 0 ? decimal.toBigInteger().toString() : decimal.toPlainString();
    }

    private double orDefault(Double value, double fallback) {
        return value == null ? fallback : value;
    }
}
