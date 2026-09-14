package bio.cosy.feddb.core.services.orch.dto;

import lombok.Data;

/**
 * Data Transfer Object (DTO) representing a Docker container from Docker API responses.
 * Maps directly to the JSON structure returned by Docker's REST API endpoints.
 */
@Data
public class RuntimeInfoDTO {
    private String path;
}
