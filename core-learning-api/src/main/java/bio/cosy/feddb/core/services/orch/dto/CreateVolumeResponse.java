package bio.cosy.feddb.core.services.orch.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.Map;

/**
 * Data Transfer Object (DTO) representing a Docker container from Docker API responses.
 * Maps directly to the JSON structure returned by Docker's REST API endpoints.
 */
@Data
public class CreateVolumeResponse {
    @JsonProperty("Name")
    private String name;
    @JsonProperty("Labels")
    private Map<String, String> labels;
    @JsonProperty("Driver")
    private String driver;
    @JsonProperty("Mountpoint")
    private String mountpoint;
}
