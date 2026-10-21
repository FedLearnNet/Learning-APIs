package bio.cosy.feddb.core.api.pipeline;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;

@Data
public class SastFindingDTO {
    private String ruleId;
    private String severity;
    private String message;
    private String filePath;
    private int startLine;
    private Integer endLine;
    private Integer startColumn;
    private Integer endColumn;
    private Map<String, Object> metadata = new HashMap<>();
}
