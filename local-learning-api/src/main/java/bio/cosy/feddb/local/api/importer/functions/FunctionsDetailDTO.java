package bio.cosy.feddb.local.api.importer.functions;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class FunctionsDetailDTO {
    private String moduleName;
    private String methodName;
    private String description;
    private FunctionExecutionMode mode;
    private List<FunctionParameterDTO> parameters = new ArrayList<>();
    private List<String> returnKeys = new ArrayList<>();
    private List<String> choices;
}
