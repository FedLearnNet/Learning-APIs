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
        return "Replace one exact cell value by another value or by empty (null), other cells stay unchanged. For several "
                + "values use Generic Value Mapper.";
    }

    @Override
    public List<FunctionParameterDTO> parameters() {
        return List.of(
                FunctionParameterDTO.of("search", FunctionParameterType.STRING, true, null,
                        "The whole cell value to replace, matched exactly (no part of a cell, case sensitive); 'null' matches empty cells."),
                FunctionParameterDTO.of("replace", FunctionParameterType.STRING, false, null,
                        "The new value; 'null' makes the cell empty.")
        );
    }

    @Override
    public Object execute(CellFunctionExecutionContext context) {
        Object replaceWith = context.getParams().getOrDefault("replace", null);
        if ("null".equals(replaceWith)) {
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
