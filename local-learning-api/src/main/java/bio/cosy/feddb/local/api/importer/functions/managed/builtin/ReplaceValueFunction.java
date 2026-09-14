package bio.cosy.feddb.local.api.importer.functions.managed.builtin;

import bio.cosy.feddb.local.api.importer.functions.FunctionParameterDTO;
import bio.cosy.feddb.local.api.importer.functions.FunctionParameterType;
import bio.cosy.feddb.local.api.importer.functions.managed.AbstractManagedCellFunction;
import bio.cosy.feddb.local.api.importer.functions.managed.CellFunctionExecutionContext;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

@ApplicationScoped
public class ReplaceValueFunction extends AbstractManagedCellFunction {
    @Override
    public String methodName() {
        return "Replace Value";
    }

    @Override
    public String description() {
        return "Replace Value with other value or null";
    }

    @Override
    public List<FunctionParameterDTO> parameters() {
        return List.of(
                FunctionParameterDTO.of("search", FunctionParameterType.STRING, true, null,
                        "The value to search for in the cell (use 'null' to match null values)."),
                FunctionParameterDTO.of("replace", FunctionParameterType.STRING, false, null,
                        "The replacement value (use 'null' to replace with null).")
        );
    }

    @Override
    public Object execute(CellFunctionExecutionContext context) {
        Object replaceWith = context.getParams().getOrDefault("replace", null);
        if (replaceWith.equals("null")) {
            replaceWith = null;
        }
        String searchValue = String.valueOf(context.getParams().get("search"));
        Object value = context.getValue();
        boolean found = (value == null && searchValue.equals("null")) ||
                (value != null && value.toString().equals(searchValue));
        if (!found) {
            return value;
        }
        String logMessage = String.format("Replacing value '%s' with '%s'", value, replaceWith);
        logInfo(logMessage, context.getTransformerId(), context.getRunId());
        return replaceWith;
    }
}
