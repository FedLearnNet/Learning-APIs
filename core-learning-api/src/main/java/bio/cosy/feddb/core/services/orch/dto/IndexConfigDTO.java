package bio.cosy.feddb.core.services.orch.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * Data Transfer Object (DTO) representing a Docker container from Docker API responses.
 * Maps directly to the JSON structure returned by Docker's REST API endpoints.
 */
@Data
public class IndexConfigDTO {
    @JsonProperty("Mirrors")
    private List<String> mirrors;
    @JsonProperty("Name")
    private String name;
    @JsonProperty("Official")
    private Boolean official;
    @JsonProperty("Secure")
    private Boolean secure;
}
