package bio.cosy.feddb.local.api.importer.functions.managed.builtin;

import bio.cosy.feddb.local.api.importer.functions.FunctionParameterDTO;
import bio.cosy.feddb.local.api.importer.functions.FunctionParameterType;
import bio.cosy.feddb.local.api.importer.functions.managed.AbstractManagedCellFunction;
import bio.cosy.feddb.local.api.importer.functions.managed.CellFunctionExecutionContext;
import jakarta.enterprise.context.ApplicationScoped;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

/**
 * Rescale a numeric cell by a constant factor to reverse a site-level unit
 * change (e.g. a site that reports length of stay in hours when the shared
 * schema expects days, or a lab value in mmol/L vs mg/dL).
 * <p>
 * The new value is {@code value * multiplier / divisor}, rounded to
 * {@code decimals} fractional digits ({@code decimals = 0} yields an integer,
 * the common case for count/day variables). Empty or non-numeric cells pass
 * through unchanged so that validation/coercion of genuinely bad values is left
 * to the schema layer.
 */
@ApplicationScoped
public class ScaleNumericFunction extends AbstractManagedCellFunction {

    @Override
    public String methodName() {
        return "Scale Numeric";
    }

    @Override
    public String description() {
        return "Rescale a numeric value by multiplier/divisor (e.g. unit conversion such as hours to days).";
    }

    @Override
    public List<FunctionParameterDTO> parameters() {
        return List.of(
                FunctionParameterDTO.of("multiplier", FunctionParameterType.DOUBLE, false, "1",
                        "Numerator factor applied before division."),
                FunctionParameterDTO.of("divisor", FunctionParameterType.DOUBLE, false, "1",
                        "Denominator factor; the cell value is multiplied by the numerator and divided by this value."),
                FunctionParameterDTO.of("decimals", FunctionParameterType.INTEGER, false, "0",
                        "Number of fractional digits to keep in the result (0 yields an integer).")
        );
    }

    @Override
    public Object execute(CellFunctionExecutionContext context) {
        Object value = context.getValue();
        if (value == null || value.toString().isEmpty()) {
            return value;
        }
        Map<String, Object> params = context.getParams();
        double multiplier = parseDouble(params.getOrDefault("multiplier", "1"), 1.0);
        double divisor = parseDouble(params.getOrDefault("divisor", "1"), 1.0);
        int decimals = (int) parseDouble(params.getOrDefault("decimals", "0"), 0.0);
        if (divisor == 0.0) {
            return value;
        }
        BigDecimal result;
        try {
            result = new BigDecimal(value.toString())
                    .multiply(BigDecimal.valueOf(multiplier))
                    .divide(BigDecimal.valueOf(divisor), Math.max(decimals, 10), RoundingMode.HALF_UP)
                    .setScale(decimals, RoundingMode.HALF_UP);
        } catch (NumberFormatException ex) {
            return value; // not numeric — leave for schema-level coercion
        }
        if (decimals == 0) {
            return String.valueOf(result.longValueExact());
        }
        return result.toPlainString();
    }

    private double parseDouble(Object raw, double fallback) {
        if (raw == null) {
            return fallback;
        }
        try {
            return Double.parseDouble(raw.toString());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }
}
