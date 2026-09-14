package bio.cosy.feddb.core.services.orch.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Data Transfer Object (DTO) representing a Docker container from Docker API responses.
 * Maps directly to the JSON structure returned by Docker's REST API endpoints.
 */
@Data
public class InfoRegistryConfigDTO {
    @JsonProperty("IndexConfigs")
    private Map<String, IndexConfigDTO> indexConfigs;
    @JsonProperty("InsecureRegistryCIDRs")
    private List<String> insecureRegistryCIDRs;
    @JsonProperty("Mirrors")
    private Object mirrors;
}
