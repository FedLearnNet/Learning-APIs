package bio.cosy.feddb.core.api.app;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class FederatedAppPublishDTO {


    @NotBlank(message = "Changelog is required")
    private String changelog;

}
