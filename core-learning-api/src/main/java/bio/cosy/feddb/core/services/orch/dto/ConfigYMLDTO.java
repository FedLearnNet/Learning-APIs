package bio.cosy.feddb.core.services.orch.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ConfigYMLDTO {
    @NotNull(message = "Content must not be null")
    String content;
    String fileName;
    String fileType;
}
