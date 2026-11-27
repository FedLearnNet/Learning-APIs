package bio.cosy.feddb.local.api.importer.functions.managed.builtin;

import bio.cosy.feddb.local.api.importer.functions.FunctionExecutionMode;
import bio.cosy.feddb.local.api.importer.functions.FunctionParameterDTO;
import bio.cosy.feddb.local.api.importer.functions.FunctionParameterType;
import bio.cosy.feddb.local.api.importer.functions.FunctionParameterUsage;
import bio.cosy.feddb.local.api.importer.functions.managed.AbstractManagedFunction;
import tools.jackson.core.type.TypeReference;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Turns a wide table (one column per measurement, e.g. one column per lab test, each with an
 * optional unit column) into one row per measurement.
 * <p>
 * The loader builds one list instance per source row, so a measurement's name, value, unit and
 * date only stay together when each measurement has a row of its own. Per input row:
 * <ul>
 *   <li>the original row is kept with the configured value and unit columns removed, so its
 *       other data (e.g. atomic attributes) is loaded exactly once;</li>
 *   <li>every filled value column adds a new row after it. That row has the same columns, all
 *       {@code null} — which mapping skips — except the patient id and visit columns, copied so
 *       the row stays with its patient and visit, and the outputs.</li>
 * </ul>
 * It extends {@link AbstractManagedFunction} directly because it returns more rows than it
 * receives, which the row and patient base classes do not allow.
 */
@ApplicationScoped
public class WideToLongFunction extends AbstractManagedFunction {

    static final String PATIENT_ID = "patient id";
    static final String VISIT_ID = "visit id";
    static final String VISIT_TIMESTAMP = "visit timestamp";
    static final String START_DATE = "start date";
    static final String END_DATE = "end date";
    static final String MEASUREMENT_COLUMNS = "measurement columns";

    static final String VARIABLE_NAME = "variable name";
    static final String VARIABLE_VALUE = "variable value";
    static final String VARIABLE_UNIT = "variable unit";

    private static final String EXAMPLE = "{\"HbA1c\": \"HbA1c unit\", \"Glucose\": \"Glucose unit\", \"BMI\": null}";

    @Override
    public String methodName() {
        return "Wide To Long";
    }

    @Override
    public String description() {
        return "Turn a table with one column per measurement (e.g. one column per lab test) into one row per "
                + "measurement. Each new row gets the measurement's name, value and unit, so they can be mapped to "
                + "the schema like any other list.";
    }

    @Override
    public FunctionExecutionMode mode() {
        return FunctionExecutionMode.ROW;
    }

    @Override
    public List<FunctionParameterDTO> parameters() {
        return List.of(
                input(PATIENT_ID, true,
                        "The column with the patient ID. It is copied to every new row so each measurement stays "
                                + "with its patient."),
                input(VISIT_ID, false,
                        "The column with the visit ID, if there is one. It is copied to every new row."),
                input(VISIT_TIMESTAMP, false,
                        "The column with the visit date/time, if there is one. It is copied to every new row."),
                input(START_DATE, false,
                        "The column with the date of the measurement (or the date it started). It is written to "
                                + "the output 'start date'."),
                input(END_DATE, false,
                        "The column with the date the measurement ended, if there is one. It is written to the "
                                + "output 'end date'."),
                new FunctionParameterDTO(MEASUREMENT_COLUMNS, FunctionParameterType.MAP, true, null,
                        "Which columns hold the measurements, and which column holds the unit of each one. Write "
                                + "each value column, a colon, then its unit column in quotes, or null if it has no "
                                + "unit column. Example: " + EXAMPLE + ". This makes one new row for HbA1c, one for "
                                + "Glucose and one for BMI, wherever these cells are filled. Columns not listed here, "
                                + "such as range columns, stay as they are.",
                        null, FunctionParameterUsage.HYPERPARAMETER)
        );
    }

    @Override
    public List<String> returnKeys() {
        return List.of(VARIABLE_NAME, VARIABLE_VALUE, VARIABLE_UNIT, START_DATE, END_DATE);
    }

    @Override
    public List<Map<String, Object>> apply(List<Map<String, Object>> rows,
                                           String column,
                                           Map<String, String> inputMapping,
                                           Map<String, String> returnMapping,
                                           Map<String, Object> hyperparams,
                                           String cohortId,
                                           Long transformerId,
                                           Long runId) {
        Map<String, String> measurements = readMeasurementColumns(
                hyperparams == null ? null : hyperparams.get(MEASUREMENT_COLUMNS));
        String patientColumn = columnOf(inputMapping, PATIENT_ID);
        if (patientColumn == null) {
            throw new IllegalArgumentException("Wide To Long: pick the column with the patient ID for '"
                    + PATIENT_ID + "', so every new row stays with its patient.");
        }
        List<String> copiedColumns = new ArrayList<>();
        copiedColumns.add(patientColumn);
        for (String input : List.of(VISIT_ID, VISIT_TIMESTAMP)) {
            String visitColumn = columnOf(inputMapping, input);
            if (visitColumn != null) {
                copiedColumns.add(visitColumn);
            }
        }
        if (rows == null || rows.isEmpty()) {
            return rows;
        }

        logMissingColumns(rows, measurements, transformerId, runId);

        List<Map<String, Object>> result = new ArrayList<>(rows.size());
        int added = 0;
        for (Map<String, Object> row : rows) {
            Object startDate = resolve(inputMapping, START_DATE, row);
            Object endDate = resolve(inputMapping, END_DATE, row);
            Map<String, Object> copied = new LinkedHashMap<>();
            copiedColumns.forEach(name -> copied.put(name, row.get(name)));

            List<Map<String, Object>> newRows = new ArrayList<>();
            for (Map.Entry<String, String> measurement : measurements.entrySet()) {
                Object value = row.get(measurement.getKey());
                if (BuiltInFunctionSupport.isBlank(value)) {
                    continue;
                }
                Object unit = measurement.getValue() == null ? null : row.get(measurement.getValue());
                Map<String, Object> outputs = new LinkedHashMap<>();
                outputs.put(VARIABLE_NAME, measurement.getKey());
                outputs.put(VARIABLE_VALUE, value);
                outputs.put(VARIABLE_UNIT, BuiltInFunctionSupport.isBlank(unit) ? null : unit);
                outputs.put(START_DATE, startDate);
                outputs.put(END_DATE, endDate);
                newRows.add(outputs);
            }

            for (Map.Entry<String, String> measurement : measurements.entrySet()) {
                row.remove(measurement.getKey());
                if (measurement.getValue() != null) {
                    row.remove(measurement.getValue());
                }
            }
            // Same columns on every row for the preview; an output mapped onto an existing column keeps its data
            returnKeys().forEach(key -> row.putIfAbsent(outputColumn(key, returnMapping), null));
            result.add(row);

            for (Map<String, Object> outputs : newRows) {
                Map<String, Object> newRow = new LinkedHashMap<>();
                row.keySet().forEach(name -> newRow.put(name, null));
                newRow.putAll(copied);
                writeOutputs(newRow, outputs, returnMapping);
                result.add(newRow);
            }
            added += newRows.size();
        }

        logInfo(String.format("Wide To Long: added %d measurement rows to %d rows", added, rows.size()),
                transformerId, runId);
        return result;
    }

    private static FunctionParameterDTO input(String name, boolean required, String description) {
        return new FunctionParameterDTO(name, FunctionParameterType.STRING, required, null, description, null,
                FunctionParameterUsage.INPUT);
    }

    /** Reads the value column → unit column object, keeping the order the user wrote it in. */
    static Map<String, String> readMeasurementColumns(Object raw) {
        if (raw instanceof List<?> || raw instanceof String text && text.strip().startsWith("[")) {
            throw invalidMeasurementColumns("it is a list, but it needs pairs of value column and unit column");
        }
        Map<String, Object> parsed;
        try {
            parsed = BuiltInFunctionSupport.readJson(raw, new TypeReference<LinkedHashMap<String, Object>>() {
            }, MEASUREMENT_COLUMNS);
        } catch (IllegalArgumentException e) {
            throw invalidMeasurementColumns("it could not be read");
        }
        if (parsed == null || parsed.isEmpty()) {
            throw invalidMeasurementColumns("no columns are given");
        }
        Map<String, String> measurements = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : parsed.entrySet()) {
            String valueColumn = entry.getKey().strip();
            if (valueColumn.isEmpty()) {
                throw invalidMeasurementColumns("one value column has no name");
            }
            String unitColumn = entry.getValue() == null ? "" : String.valueOf(entry.getValue()).strip();
            measurements.put(valueColumn, unitColumn.isEmpty() || "null".equals(unitColumn) ? null : unitColumn);
        }
        return measurements;
    }

    private static IllegalArgumentException invalidMeasurementColumns(String reason) {
        return new IllegalArgumentException("Wide To Long: '" + MEASUREMENT_COLUMNS + "' is not valid, " + reason
                + ". Write each value column, a colon, then its unit column (or null), e.g. " + EXAMPLE);
    }

    /** The column an input is mapped to, or {@code null} when it is not mapped to a column. */
    private static String columnOf(Map<String, String> inputMapping, String input) {
        String spec = inputMapping == null ? null : inputMapping.get(input);
        if (spec == null || spec.isBlank()) {
            return null;
        }
        if (spec.startsWith(VALUE_PREFIX)) {
            throw new IllegalArgumentException("Wide To Long: '" + input + "' needs a column, not a fixed value.");
        }
        return spec;
    }

    /** The value of an optional input for this row: the cell of its column, or its fixed value. */
    private static Object resolve(Map<String, String> inputMapping, String input, Map<String, Object> row) {
        String spec = inputMapping == null ? null : inputMapping.get(input);
        if (spec == null || spec.isBlank()) {
            return null;
        }
        if (spec.startsWith(VALUE_PREFIX)) {
            return spec.substring(VALUE_PREFIX.length());
        }
        return row.get(spec);
    }

    private static void writeOutputs(Map<String, Object> row, Map<String, Object> outputs,
                                     Map<String, String> returnMapping) {
        outputs.forEach((key, value) -> row.put(outputColumn(key, returnMapping), value));
    }

    /** The column an output is written to: its return mapping, else the output's own name. */
    private static String outputColumn(String key, Map<String, String> returnMapping) {
        String target = returnMapping == null ? null : returnMapping.get(key);
        return target == null || target.isBlank() ? key : target;
    }

    private void logMissingColumns(List<Map<String, Object>> rows, Map<String, String> measurements,
                                   Long transformerId, Long runId) {
        Set<String> known = new LinkedHashSet<>();
        rows.forEach(row -> known.addAll(row.keySet()));
        Set<String> missing = new LinkedHashSet<>();
        for (Map.Entry<String, String> measurement : measurements.entrySet()) {
            if (!known.contains(measurement.getKey())) {
                missing.add(measurement.getKey());
            }
            if (measurement.getValue() != null && !known.contains(measurement.getValue())) {
                missing.add(measurement.getValue());
            }
        }
        if (!missing.isEmpty()) {
            // Almost always a typo in 'measurement columns' or a step that runs before the one creating the column
            logInfo("Wide To Long: no column named " + String.join(", ", missing), transformerId, runId);
        }
    }
}
