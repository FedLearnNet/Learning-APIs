package bio.cosy.feddb.local.api.importer.transformer.code;

import bio.cosy.feddb.core.api.datamodler.datatype.DataTypeNodeDTO;
import bio.cosy.feddb.local.api.importer.functions.FunctionExecutionMode;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class CodeTransformerCheckDTO {
    private String requestId;
    private String body;
    private FunctionExecutionMode mode;
    private DataTypeNodeDTO goal;
    private Map<String, DataTypeNodeDTO> sourceGoals;
    private Map<String, List<Object>> data;
}
