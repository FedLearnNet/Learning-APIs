package de.unihamburg.daibetes.api.app;

import bio.cosy.feddb.core.api.app.FederatedAppType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class AppCreateDTO {

    @NotBlank(message = "Name is required")
    private String name;

    @NotBlank(message = "Slug is required")
    @Pattern(regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$", message = "Slug should contain only letters and digits separated by a hyphen.")
    private String slug;

    @NotBlank(message = "Short description is required")
    private String shortDescription;

    @NotNull(message = "Tool type is required")
    private FederatedAppType type;

    private Boolean supportsFederatedLearning;
}

