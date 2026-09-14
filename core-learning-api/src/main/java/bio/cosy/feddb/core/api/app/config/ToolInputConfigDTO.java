package bio.cosy.feddb.core.api.app.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ToolInputConfigDTO extends ToolConfigDTO {
    private boolean required;
}
