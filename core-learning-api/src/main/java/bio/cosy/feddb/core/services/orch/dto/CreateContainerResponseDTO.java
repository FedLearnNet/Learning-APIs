package bio.cosy.feddb.core.services.orch.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * Data Transfer Object (DTO) representing a Docker container from Docker API responses.
 * Maps directly to the JSON structure returned by Docker's REST API endpoints.
 */
@Data
public class CreateContainerResponseDTO {
    @JsonProperty("Id")
    private String id;
    @JsonProperty("Warnings")
    private String[] warnings;
}
