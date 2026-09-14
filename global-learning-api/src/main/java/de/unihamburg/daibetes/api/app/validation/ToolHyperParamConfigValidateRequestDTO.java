package de.unihamburg.daibetes.api.app.validation;

import bio.cosy.feddb.core.api.app.config.ToolHyperParamConfigDTO;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ToolHyperParamConfigValidateRequestDTO {
    @NotNull(message = "Config must not be null")
    private ToolHyperParamConfigDTO config;

    @NotNull(message = "Value must not be null")
    private Object value;

}
