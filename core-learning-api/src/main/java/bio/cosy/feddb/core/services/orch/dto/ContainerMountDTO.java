package bio.cosy.feddb.core.services.orch.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * Data Transfer Object (DTO) representing a Docker container from Docker API responses.
 * Maps directly to the JSON structure returned by Docker's REST API endpoints.
 */
@Data
public class ContainerMountDTO {
    @JsonProperty("Name")
    String name;
    @JsonProperty("Source")
    String source;
    @JsonProperty("Destination")
    String destination;
    @JsonProperty("Driver")
    String driver;
    @JsonProperty("Mode")
    String mode;
    @JsonProperty("RW")
    boolean rw;
    @JsonProperty("Propagation")
    String propagation;
}
