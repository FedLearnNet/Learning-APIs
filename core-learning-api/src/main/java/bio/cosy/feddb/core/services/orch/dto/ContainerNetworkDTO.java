package bio.cosy.feddb.core.services.orch.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * Data Transfer Object (DTO) representing a Docker container from Docker API responses.
 * Maps directly to the JSON structure returned by Docker's REST API endpoints.
 */
@Data
public class ContainerNetworkDTO {
    @JsonProperty("IPAMConfig")
    private IpamDTO ipamConfig;
    @JsonProperty("Links")
    private LinksDTO links;
    @JsonProperty("Aliases")
    private List<String> aliases;
    @JsonProperty("NetworkID")
    private String networkID;
    @JsonProperty("EndpointID")
    private String endpointId;
    @JsonProperty("Gateway")
    private String gateway;
    @JsonProperty("IPAddress")
    private String ipAddress;
    @JsonProperty("IPPrefixLen")
    private Integer ipPrefixLen;
    @JsonProperty("IPv6Gateway")
    private String ipV6Gateway;
    @JsonProperty("GlobalIPv6Address")
    private String globalIPv6Address;
    @JsonProperty("GlobalIPv6PrefixLen")
    private Integer globalIPv6PrefixLen;
    @JsonProperty("MacAddress")
    private String macAddress;
}
