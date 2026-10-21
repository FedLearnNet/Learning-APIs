package bio.cosy.feddb.core.api.pipeline;

import lombok.Data;

@Data
public class SastScanErrorDTO {
    private String type;
    private String message;
    private String level;
    private Integer code;
    private String filePath;
}
