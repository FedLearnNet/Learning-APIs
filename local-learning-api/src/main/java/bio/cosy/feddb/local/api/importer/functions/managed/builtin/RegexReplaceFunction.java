package bio.cosy.feddb.local.api.importer.functions.managed.builtin;

import bio.cosy.feddb.local.api.importer.functions.FunctionParameterDTO;
import bio.cosy.feddb.local.api.importer.functions.FunctionParameterType;
import bio.cosy.feddb.local.api.importer.functions.managed.AbstractManagedCellFunction;
import bio.cosy.feddb.local.api.importer.functions.managed.CellFunctionExecutionContext;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Map;

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
        return "Replace all matches of a regular expression with a replacement string (default empty).";
    }

    @Override
    public List<FunctionParameterDTO> parameters() {
        return List.of(
                FunctionParameterDTO.of("pattern", FunctionParameterType.STRING, true, null,
                        "Java regular expression to match against the cell value."),
                FunctionParameterDTO.of("replacement", FunctionParameterType.STRING, false, "",
                        "Replacement string for each match (default empty).")
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
        return value.toString().replaceAll(pattern, replacement);
    }
}
