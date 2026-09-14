package bio.cosy.feddb.local.api.importer.functions.managed.builtin;

import bio.cosy.feddb.local.api.importer.functions.managed.AbstractManagedRowFunction;
import bio.cosy.feddb.local.api.importer.functions.managed.RowFunctionExecutionContext;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Abstract base class for functions that split a single input column into multiple boolean flag columns.
 * <p>
 * Subclasses must implement:
 * - methodName()
 * - description()
 * - parameters()
 * - paramColumn()        - the input column name to read from
 * - returnKeys()         - the output column names
 * - nullLabel()          - label used when input is null/empty (e.g. "Unknown")
 * - noLabel()            - label used when input is an explicit negative (e.g. "No")
 * - yesLabel()           - label used when a condition is matched (e.g. "Yes")
 * - noValues()           - normalized input strings that map to all-noLabel (e.g. "none", "no")
 * - inputToOutput()      - mapping of normalized input tokens to output keys (e.g. "diabetes" -> "Diabetes Mellitus")
 * Subclasses may optionally override:
 * - connectorPattern()   - regex pattern to split multi-value inputs (null if single-value only)
 * Input values from the paramsColumn are normalized by trimming and lowercasing.
 */
abstract class AbstractSplitFlagsFunction extends AbstractManagedRowFunction {

    @Override
    public Map<String, Object> execute(RowFunctionExecutionContext context) {
        Map<String, Object> row = context.getRow();

        Object rawValue = row.get(paramColumn());

        // null/empty -> all nullLabel
        if (rawValue == null || rawValue.toString().isBlank()) {
            return allKeys(nullLabel());
        }

        String normalized = rawValue.toString().strip().toLowerCase();

        // explicit negative strings -> all noLabel
        if (noValues().contains(normalized)) {
            return allKeys(noLabel());
        }

        // tokenize (single value if no connector pattern)
        List<String> tokens;
        Pattern connector = connectorPattern();
        if (connector != null) {
            // split on connectors, trim each token, filter out empties
            tokens = Arrays.stream(connector.split(normalized))
                .map(String::strip)
                .filter(t -> !t.isEmpty())
                .toList();
        } else {
            tokens = List.of(normalized);
        }

        // start with all noLabel
        Map<String, Object> result = new LinkedHashMap<>();
        for (String key : returnKeys()) {
            result.put(key, noLabel());
        }

        Set<String> matched = new LinkedHashSet<>();
        for (String token : tokens) {
            String outputKey = inputToOutput().get(token);
            if (outputKey != null) {
                result.put(outputKey, yesLabel());
                matched.add(outputKey);
            }
        }

        if (matched.isEmpty()) {
            throw new IllegalArgumentException(
                "No valid value found in input: '" + rawValue + "'. " +
                "Supported values are: " + inputToOutput().keySet()
            );
        }

        return result;
    }

    private Map<String, Object> allKeys(Object label) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (String key : returnKeys()) {
            result.put(key, label);
        }
        return result;
    }

    protected abstract String paramColumn();
    protected abstract Set<String> noValues();
    protected abstract Map<String, String> inputToOutput();

    /*
    * By default we split on:
    * - commas
    * - semicolons
    * - the word "and"
    * Override connectorPattern() to change this (e.g. if your function is single-value
    */
    public Pattern connectorPattern() {
        return Pattern.compile("\\s*(,|;|and)\\s*");
    }

    protected String nullLabel() { return "Unknown"; }
    protected String noLabel()   { return "No"; }
    protected String yesLabel()  { return "Yes"; }
}
