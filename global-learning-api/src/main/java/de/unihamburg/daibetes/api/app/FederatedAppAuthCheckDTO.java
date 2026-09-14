package de.unihamburg.daibetes.api.app;


import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class FederatedAppAuthCheckDTO {
    @NotBlank(message = "Image Name is required")
    private String imageName;
    @NotBlank(message = "Username is required")
    private String username;
}

