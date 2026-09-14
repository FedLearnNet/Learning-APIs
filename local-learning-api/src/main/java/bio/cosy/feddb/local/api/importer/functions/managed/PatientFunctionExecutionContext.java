package bio.cosy.feddb.local.api.importer.functions.managed;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class PatientFunctionExecutionContext {
    private List<Map<String, Object>> rows;
    private Map<String, Object> params;
    /** Source-row indexes in the order in which execute() returns its result maps. */
    private List<Integer> rowOrder;
    private Long transformerId;
    private Long runId;
    private String cohortId;
}
