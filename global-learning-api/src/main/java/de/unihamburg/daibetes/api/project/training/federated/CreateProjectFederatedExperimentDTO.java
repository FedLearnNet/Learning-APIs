package de.unihamburg.daibetes.api.project.training.federated;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateProjectFederatedExperimentDTO {

    @NotBlank(message = "Name is mandatory")
    private String name;
    @NotBlank(message = "Description is mandatory")
    private String description;

    private Boolean modelNeedToBePublic;

}
