package bio.cosy.feddb.core.api.app.config.validation;

import bio.cosy.feddb.core.api.app.config.ToolConfigHyperParamDataType;
import bio.cosy.feddb.core.api.app.config.ToolHyperParamConfigDTO;
import bio.cosy.feddb.core.base.BaseValidationResultDTO;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public class HyperParamValidator {

    public static boolean validateHyperParamValues(List<ToolHyperParamConfigDTO> hyperparams,
                                                   Map<String, Object> values) {
        if (hyperparams == null) {
            return true;
        }
        if (values == null && hyperparams.isEmpty()) {
            return true;
        }
        if (values == null) {
            return false;
        }

        for (ToolHyperParamConfigDTO h : hyperparams) {
            if (h == null || isBlank(h.getVariableName())) {
                return false;
            }
            Object value = values.get(h.getVariableName());
            if (value == null) {
                return false;
            }
            BaseValidationResultDTO result = validateInputValues(value, h);
            if (result == null || !result.isOk()) {
                return false;
            }
        }
        return true;
    }

    public static BaseValidationResultDTO validateInputValues(Object rawValue, ToolHyperParamConfigDTO config) {
        List<String> errorMessages = new ArrayList<>();
        boolean hasError = false;

        List<Object> parsed = getParsedValues(rawValue, config);
        if (parsed == null) {
            errorMessages.add("Input is required");
            return BaseValidationResultDTO.fail(errorMessages);
        }

        if (rawValue != null && parsed.isEmpty()) {
            if (isNumber(config)) {
                errorMessages.add("Value '" + rawValue + "' is not a valid number");
            } else {
                errorMessages.add("Value '" + rawValue + "' can be parsed");
            }
            return BaseValidationResultDTO.fail(errorMessages);
        }

        for (Object partObj : parsed) {
            String partStr = partObj == null ? null : String.valueOf(partObj);

            if (isNumber(config)) {
                Double part = asDouble(partObj);
                if (part == null || part.isNaN() || part.isInfinite()) {
                    errorMessages.add("Value '" + partStr + "' is not a valid number");
                    hasError = true;
                    continue;
                }
                if (config.getMinValue() != null && part < config.getMinValue()) {
                    errorMessages.add("Value ‘" + part + "’ is less than the minimum " + config.getMinValue());
                    hasError = true;
                }
                if (config.getMaxValue() != null && part > config.getMaxValue()) {
                    errorMessages.add("Value ‘" + part + "’ is greater than the maximum " + config.getMaxValue());
                    hasError = true;
                }
            }

            if (config.getType() == ToolConfigHyperParamDataType.STRING && !isBlank(config.getPattern())) {
                Pattern p;
                try {
                    p = Pattern.compile(config.getPattern());
                } catch (Exception e) {
                    errorMessages.add("Invalid validation pattern: " + config.getPattern());
                    hasError = true;
                    continue;
                }
                if (partStr == null || !p.matcher(partStr).find()) {
                    errorMessages.add("Value ‘" + partStr + "’ does not match the pattern. " + config.getPattern());
                    hasError = true;
                }
            }

            if (config.getType() == ToolConfigHyperParamDataType.CATEGORICAL) {
                List<String> options = config.getOptions() == null ? Collections.emptyList() : config.getOptions();
                if (!options.isEmpty() && (partStr == null || !options.contains(partStr))) {
                    errorMessages.add("Value ‘" + partStr + "’ is not a valid option");
                    hasError = true;
                }
            }
        }

        return hasError ? BaseValidationResultDTO.fail(errorMessages) : BaseValidationResultDTO.ok();
    }


    public static List<Object> getParsedValues(Object rawValue, ToolHyperParamConfigDTO config) {
        String input = rawValue == null ? "" : String.valueOf(rawValue);
        if (isBlank(input)) {
            return null;
        }

        List<Object> out = new ArrayList<>();
        String[] parts = input.split(",");

        for (String p : parts) {
            String part = p == null ? "" : p.trim();
            if (part.isEmpty()) {
                continue;
            }

            if (part.contains("-")) {
                String[] range = part.split("-", 2);
                if (range.length == 2 && isNumber(config)) {
                    Double start = parseNumber(range[0].trim(), config);
                    Double end = parseNumber(range[1].trim(), config);
                    if (start != null && end != null) {
                        out.addAll(expandRange(start, end, config));
                        continue;
                    }
                }
            }

            if (isNumber(config)) {
                Double v = parseNumber(part, config);
                if (v != null) {
                    out.add(v);
                }
            } else if (config.getType() == ToolConfigHyperParamDataType.BOOLEAN) {
                out.add("true".equals(part));
            } else {
                out.add(part);
            }
        }

        return out;
    }

    public static boolean isNumber(ToolHyperParamConfigDTO config) {
        ToolConfigHyperParamDataType t = config == null ? null : config.getType();
        return t == ToolConfigHyperParamDataType.INTEGER || t == ToolConfigHyperParamDataType.FLOAT;
    }

    public static Double parseNumber(String valueStr, ToolHyperParamConfigDTO config) {
        if (isBlank(valueStr) || config == null || config.getType() == null) {
            return null;
        }

        try {
            if (config.getType() == ToolConfigHyperParamDataType.INTEGER) {
                if (!valueStr.matches("^-?\\d+$")) {
                    return null;
                }
                return (double) Long.parseLong(valueStr);
            }
            if (config.getType() == ToolConfigHyperParamDataType.FLOAT) {
                if (!valueStr.matches("^-?\\d+(\\.\\d+)?$")) {
                    return null;
                }
                return Double.parseDouble(valueStr);
            }
        } catch (Exception ignored) {
            return null;
        }
        return null;
    }

    public static double getStep(ToolHyperParamConfigDTO config) {
        if (config != null && config.getType() == ToolConfigHyperParamDataType.FLOAT) {
            return 0.1d;
        }
        return 1d;
    }

    public static List<Double> expandRange(double start, double end, ToolHyperParamConfigDTO config) {
        if (end < start) {
            return List.of();
        }

        double step = getStep(config);
        List<Double> values = new ArrayList<>();

        BigDecimal bdStart = BigDecimal.valueOf(start);
        BigDecimal bdEnd = BigDecimal.valueOf(end);
        BigDecimal bdStep = BigDecimal.valueOf(step);

        BigDecimal cur = bdStart;
        while (cur.compareTo(bdEnd) <= 0) {
            BigDecimal fixed = cur.setScale(10, RoundingMode.HALF_UP).stripTrailingZeros();
            values.add(fixed.doubleValue());
            cur = cur.add(bdStep);
        }
        return values;
    }

    public static Double asDouble(Object o) {
        if (o == null) return null;
        if (o instanceof Double d) return d;
        if (o instanceof Float f) return (double) f;
        if (o instanceof Integer i) return (double) i;
        if (o instanceof Long l) return (double) l;
        if (o instanceof Short s) return (double) s;
        if (o instanceof BigDecimal bd) return bd.doubleValue();
        try {
            return Double.parseDouble(String.valueOf(o));
        } catch (Exception e) {
            return null;
        }
    }

    public static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
