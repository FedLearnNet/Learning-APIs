package bio.cosy.feddb.local.api.importer.functions.managed;

import lombok.Data;

import java.util.Map;

@Data
public class RowFunctionExecutionContext {
    private Map<String, Object> row;
    private Map<String, Object> params;
    private Long transformerId;
    private Long runId;
    private String cohortId;
}
