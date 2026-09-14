package de.unihamburg.daibetes.api.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;


@Data
public class ModelCreateForImageDTO {

    @NotBlank(message = "name is mandatory")
    private String name;

    @NotNull(message = "Federated app id is mandatory")
    private Long federatedAppId;

    @NotNull(message = "Federated app version id is mandatory")
    private Long federatedAppVersionId;


}

