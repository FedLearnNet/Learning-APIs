package bio.cosy.feddb.local.api.importer.functions.managed;

import java.util.List;
import java.util.Map;

public interface ManagedPatientFunction {

    /**
     * Returns one result map for every patient row. Returning an empty list filters
     * the complete patient from the transformation pipeline.
     */
    List<Map<String, Object>> execute(PatientFunctionExecutionContext context);
}
