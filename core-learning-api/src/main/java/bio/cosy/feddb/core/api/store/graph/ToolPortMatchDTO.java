package bio.cosy.feddb.core.api.store.graph;

import bio.cosy.feddb.core.api.app.config.ToolConfigDataType;
import lombok.Data;

@Data
public class ToolPortMatchDTO {
    private String outputVariable;
    private String inputVariable;

    private ToolConfigDataType outputType;
    private ToolConfigDataType inputType;

    private String reason;
}
