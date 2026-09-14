package bio.cosy.feddb.local.api.importer.functions.managed.builtin;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class BuiltInFunctionSupport {

    /**
     * Shared, stateless mapper for the JSON-encoded hyperparameters (code lists,
     * threshold bins, ...) the built-ins accept. Static rather than injected so
     * the functions stay unit-testable without a CDI container.
     */
    private static final ObjectMapper JSON = new ObjectMapper();

    private static final Map<String, String> PYTHON_TO_JAVA_DATE_FORMATS = Map.ofEntries(
            Map.entry("%Y", "yyyy"),
            Map.entry("%y", "yy"),
            Map.entry("%m", "MM"),
            Map.entry("%d", "dd"),
            Map.entry("%H", "HH"),
            Map.entry("%I", "hh"),
            Map.entry("%M", "mm"),
            Map.entry("%S", "ss"),
            Map.entry("%f", "SSSSSS"),
            Map.entry("%b", "MMM"),
            Map.entry("%B", "MMMM"),
            Map.entry("%a", "EEE"),
            Map.entry("%A", "EEEE"),
            Map.entry("%p", "a"),
            Map.entry("%z", "XX"),
            Map.entry("%Z", "z"),
            Map.entry("%j", "DDD"),
            Map.entry("%%", "%")
    );

    private BuiltInFunctionSupport() {
    }

    static String normalizeCodeKey(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Double d) {
            return d.isInfinite() || d.isNaN() ? String.valueOf(d) : normalizeFloatingNumber(d);
        }
        if (value instanceof Float f) {
            return f.isInfinite() || f.isNaN() ? String.valueOf(f) : normalizeFloatingNumber(f.doubleValue());
        }
        if (value instanceof Number number) {
            double asDouble = number.doubleValue();
            if (!Double.isInfinite(asDouble) && !Double.isNaN(asDouble) && asDouble == Math.floor(asDouble)) {
                return String.valueOf(number.longValue());
            }
        }
        return String.valueOf(value);
    }

    static DateTimeFormatter resolveFormatter(Object rawFormat, String defaultFormat) {
        String format = rawFormat == null ? defaultFormat : String.valueOf(rawFormat);
        format = stripMatchingQuotes(format.strip());
        format = translatePythonFormat(format);
        return DateTimeFormatter.ofPattern(format);
    }

    /** True for {@code null} and for values whose text representation is blank. */
    static boolean isBlank(Object value) {
        return value == null || String.valueOf(value).strip().isEmpty();
    }

    /** Parses a cell into a double, returning {@code null} for blank or non-numeric input. */
    static Double toDouble(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        String text = String.valueOf(value).strip();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return Double.parseDouble(text);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    /**
     * Parses a cell into a date-time, accepting an already-temporal object, the
     * configured format (as a date-time or a date), or ISO-8601. Returns
     * {@code null} when the value is blank or cannot be parsed, so callers can
     * leave genuinely bad values to the schema layer.
     */
    static LocalDateTime toDateTime(Object raw, DateTimeFormatter formatter) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof LocalDateTime dateTime) {
            return dateTime;
        }
        if (raw instanceof LocalDate date) {
            return date.atStartOfDay();
        }
        if (raw instanceof OffsetDateTime dateTime) {
            return dateTime.toLocalDateTime();
        }
        if (raw instanceof Instant instant) {
            return LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
        }
        if (raw instanceof Date date) {
            return LocalDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault());
        }

        String text = String.valueOf(raw).strip();
        if (text.isEmpty()) {
            return null;
        }
        if (formatter != null) {
            LocalDateTime parsed = parseWith(text, formatter);
            if (parsed != null) {
                return parsed;
            }
        }
        LocalDateTime isoDateTime = parseWith(text, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        if (isoDateTime != null) {
            return isoDateTime;
        }
        return parseWith(text, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    private static LocalDateTime parseWith(String text, DateTimeFormatter formatter) {
        try {
            return LocalDateTime.parse(text, formatter);
        } catch (DateTimeParseException ignored) {
            try {
                return LocalDate.parse(text, formatter).atStartOfDay();
            } catch (DateTimeParseException ignoredAgain) {
                return null;
            }
        }
    }

    /**
     * Reads a JSON-encoded hyperparameter. Accepts an already-parsed object (a
     * list or map handed over by the connector) as well as its JSON text form.
     */
    static <T> T readJson(Object raw, TypeReference<T> type, String parameterName) {
        if (raw == null) {
            return null;
        }
        try {
            if (raw instanceof String text) {
                return text.strip().isEmpty() ? null : JSON.readValue(text, type);
            }
            return JSON.convertValue(raw, type);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid '" + parameterName + "' JSON: " + raw, e);
        }
    }

    /** Reads a JSON array hyperparameter as a list of strings (an empty list when unset). */
    static List<String> readStringList(Object raw, String parameterName) {
        List<Object> values = readJson(raw, new TypeReference<List<Object>>() {
        }, parameterName);
        if (values == null) {
            return List.of();
        }
        List<String> result = new ArrayList<>(values.size());
        for (Object value : values) {
            result.add(value == null ? "" : normalizeCodeKey(value).strip());
        }
        return result;
    }

    /**
     * True when {@code value} matches one of {@code candidates} — either exactly
     * (case-insensitive, whitespace-trimmed) or, with {@code contains}, as a
     * substring. An empty candidate list matches every non-blank value.
     */
    static boolean matchesAny(Object value, List<String> candidates, boolean contains) {
        if (isBlank(value)) {
            return false;
        }
        if (candidates == null || candidates.isEmpty()) {
            return true;
        }
        String actual = normalizeCodeKey(value).strip().toLowerCase(Locale.ROOT);
        for (String candidate : candidates) {
            if (candidate == null) {
                continue;
            }
            String expected = candidate.strip().toLowerCase(Locale.ROOT);
            if (expected.isEmpty()) {
                continue;
            }
            if (contains ? actual.contains(expected) : actual.equals(expected)) {
                return true;
            }
        }
        return false;
    }

    private static String normalizeFloatingNumber(double value) {
        if (value == Math.floor(value)) {
            return String.valueOf((long) value);
        }
        return String.valueOf(value);
    }

    private static String stripMatchingQuotes(String value) {
        if (value.length() >= 2) {
            char first = value.charAt(0);
            char last = value.charAt(value.length() - 1);
            if ((first == '\'' && last == '\'') || (first == '"' && last == '"')) {
                return value.substring(1, value.length() - 1);
            }
        }
        return value;
    }

    private static String translatePythonFormat(String format) {
        String translated = format;
        for (Map.Entry<String, String> entry : PYTHON_TO_JAVA_DATE_FORMATS.entrySet()) {
            translated = translated.replace(entry.getKey(), entry.getValue());
        }
        return translated;
    }
}
