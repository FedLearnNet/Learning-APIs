package bio.cosy.feddb.local.api.importer.functions.managed.builtin;

import bio.cosy.feddb.local.api.importer.functions.FunctionParameterDTO;
import bio.cosy.feddb.local.api.importer.functions.managed.AbstractManagedCellFunction;
import bio.cosy.feddb.local.api.importer.functions.managed.CellFunctionExecutionContext;

import java.util.List;
import java.util.Map;

/**
 * Abstract base class used for any function that just maps a value to a new value.
 * Extending classes must implement
 * - methodName()
 * - description()
 * - codeMap() (returning the mapping to apply)
 * Extending classes may override parameters() if they want to use hyperparameters
 */
abstract class AbstractCodeMapperFunction extends AbstractManagedCellFunction {

    @Override
    public List<FunctionParameterDTO> parameters() {
        return List.of();
    }

    @Override
    public Object execute(CellFunctionExecutionContext context) {
        Object value = context.getValue();
        String key = BuiltInFunctionSupport.normalizeCodeKey(value);
        if (key == null) {
            return value;
        }
        return codeMap().containsKey(key) ? codeMap().get(key) : value;
    }

    protected abstract Map<String, String> codeMap();
}
