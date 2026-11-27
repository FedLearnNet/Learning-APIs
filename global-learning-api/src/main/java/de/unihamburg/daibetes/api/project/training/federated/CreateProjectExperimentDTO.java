package de.unihamburg.daibetes.api.project.training.federated;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateProjectExperimentDTO {

    @NotBlank(message = "Name is mandatory")
    private String name;
    @NotBlank(message = "Description is mandatory")
    private String description;

    private Boolean modelNeedToBePublic;

    // IF not set, the platform will decide randomly which client is coordinator
    private Boolean platformIsCoordinator;

    // TRE: the query response of the clinic to train on
    private Long queryResponseId;

}
