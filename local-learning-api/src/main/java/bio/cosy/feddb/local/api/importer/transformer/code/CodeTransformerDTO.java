package bio.cosy.feddb.local.api.importer.transformer.code;

import bio.cosy.feddb.core.api.datamodler.datatype.DataTypeNodeDTO;
import bio.cosy.feddb.core.base.BaseDTO;
import bio.cosy.feddb.local.api.importer.functions.FunctionExecutionMode;
import bio.cosy.feddb.local.api.pipeline.PipelineDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;
import java.util.Map;

@Data
@EqualsAndHashCode(callSuper = true)
public class CodeTransformerDTO extends BaseDTO {
    private String name;
    private String description;
    private FunctionExecutionMode mode;
    private String body;
    private DataTypeNodeDTO goal;
    private Map<String, DataTypeNodeDTO> sourceGoals;
    private Map<String, List<Object>> data;
    private List<String> inputKeys;
    private List<String> returnKeys;
    private Map<String, String> files;
    private String codeHash;
    private String imageName;
    private CodeTransformerStatus status;
    private PipelineDTO pipeline;
    private List<CodeTransformerUsageDTO> usages = List.of();
}
