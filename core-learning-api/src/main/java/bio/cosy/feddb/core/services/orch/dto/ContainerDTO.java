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
public class ContainerDTO {
    @JsonProperty("Command")
    private String command;
    @JsonProperty("Created")
    private Long created;
    @JsonProperty("Id")
    private String id;
    @JsonProperty("Image")
    private String image;
    @JsonProperty("ImageID")
    private String imageId;
    @JsonProperty("Names")
    private String[] names;
    @JsonProperty("Ports")
    public ContainerPortDTO[] ports;
    @JsonProperty("Labels")
    public Map<String, String> labels;
    @JsonProperty("Status")
    private String status;
    @JsonProperty("State")
    private String state;
    @JsonProperty("SizeRw")
    private Long sizeRw;
    @JsonProperty("SizeRootFs")
    private Long sizeRootFs;
    @JsonProperty("HostConfig")
    private ContainerHostConfigDTO hostConfig;
    @JsonProperty("NetworkSettings")
    private ContainerNetworkSettingsDTO networkSettings;
    @JsonProperty("Mounts")
    private List<ContainerMountDTO> mounts;
}
