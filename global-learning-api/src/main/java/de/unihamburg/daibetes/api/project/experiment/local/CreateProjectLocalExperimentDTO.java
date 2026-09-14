package de.unihamburg.daibetes.api.project.experiment.local;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateProjectLocalExperimentDTO {

    @NotBlank(message = "Name is mandatory")
    private String name;
    @NotBlank(message = "Description is mandatory")
    private String description;
}
