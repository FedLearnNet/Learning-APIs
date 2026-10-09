package de.unihamburg.daibetes.api.project;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ProjectCreateDTO {

    @NotBlank(message = "Name is mandatory")
    private String name;

    @NotBlank(message = "Description is mandatory")
    private String description;

    private Long queryId;

    //IF not set, the platform will decide randomly which client is coordinator
    private boolean platformIsCoordinator = false;
}
