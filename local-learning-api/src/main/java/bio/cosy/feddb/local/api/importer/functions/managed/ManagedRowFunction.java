package bio.cosy.feddb.local.api.importer.functions.managed;

import java.util.Map;

public interface ManagedRowFunction {

    Map<String, Object> execute(RowFunctionExecutionContext context);
}
