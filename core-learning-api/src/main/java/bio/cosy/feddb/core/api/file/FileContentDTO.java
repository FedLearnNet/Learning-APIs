package bio.cosy.feddb.core.api.file;

import bio.cosy.feddb.core.api.app.config.ToolConfigDataType;
import lombok.Data;

@Data
public class FileContentDTO {
    private String content;
    private ToolConfigDataType type;
}
