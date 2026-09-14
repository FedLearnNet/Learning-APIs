package de.unihamburg.daibetes.api.app.version;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class FederatedAppPublishDTO {

    private String changelog;

    @NotBlank(message = "Version is required")
    private String version;

    @JsonProperty("createModel")
    private Boolean createModel = false;

    @NotNull(message = "needsInternetAccess is required")
    private Boolean needsInternetAccess = false;

    @NotNull(message = "needsHostAccess is required")
    private Boolean needsHostAccess = false;
}
