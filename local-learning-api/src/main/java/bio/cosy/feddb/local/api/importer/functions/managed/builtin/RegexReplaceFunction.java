package bio.cosy.feddb.local.api.importer.functions.managed.builtin;

import bio.cosy.feddb.local.api.importer.functions.FunctionParameterDTO;
import bio.cosy.feddb.local.api.importer.functions.FunctionParameterType;
import bio.cosy.feddb.local.api.importer.functions.managed.AbstractManagedCellFunction;
import bio.cosy.feddb.local.api.importer.functions.managed.CellFunctionExecutionContext;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Apply a regular-expression substitution to a cell, reversing cosmetic
 * formatting a site introduces around its values — e.g. spacing out comparator
 * tokens ("&lt; 30" → "&lt;30"), stripping unit suffixes, or removing thousands
 * separators. Null cells pass through.
 */
@ApplicationScoped
public class RegexReplaceFunction extends AbstractManagedCellFunction {

    @Override
    public String methodName() {
        return "Regex Replace";
    }

    @Override
    public String description() {
        return "Replace every match of a Java regular expression in the cell, e.g. pattern '^([0-9]+)[.].*' with replacement "
                + "'$1' keeps the digits before a dot. Replacement 'null' makes a cell in which the pattern is found empty.";
    }

    @Override
    public List<FunctionParameterDTO> parameters() {
        return List.of(
                FunctionParameterDTO.of("pattern", FunctionParameterType.STRING, true, null,
                        "Java regular expression to match against the cell value."),
                FunctionParameterDTO.of("replacement", FunctionParameterType.STRING, false, "",
                        "Replacement for each match, $1 for the first group (default empty); 'null' empties the whole cell when the pattern is found.")
        );
    }

    @Override
    public Object execute(CellFunctionExecutionContext context) {
        Object value = context.getValue();
        if (value == null) {
            return null;
        }
        Map<String, Object> params = context.getParams();
        String pattern = String.valueOf(params.get("pattern"));
        String replacement = params.get("replacement") == null ? "" : String.valueOf(params.get("replacement"));
        if ("null".equals(replacement)) {
            // Like in Replace Value: a matching value becomes empty
            return Pattern.compile(pattern).matcher(value.toString()).find() ? null : value;
        }
        return value.toString().replaceAll(pattern, replacement);
    }
}
