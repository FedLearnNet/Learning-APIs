package bio.cosy.feddb.core.api.app;

import bio.cosy.feddb.core.base.BaseDTO;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@EqualsAndHashCode(callSuper = true)
@Data
@JsonIgnoreProperties(
        value = {"latestUnpublishedVersionId", "hasImage"},
        allowGetters = true
)
public class FederatedAppDTO extends BaseDTO {

    @NotBlank(message = "Name is required")
    private String name;

    private UUID uniqueAppId;

    @NotBlank(message = "Slug is required")
    @Pattern(regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$", message = "Slug should contain only letters and digits separated by a hyphen.")
    private String slug;


    //based on latest version;
    private AppPublishInfoDTO publishInfo;
    private String shortDescription;
    private String longDescription;
    private Boolean needsInternetAccess = false;
    private Boolean needsHostAccess = false;

    private FederatedAppType type;

    private Boolean supportsFederatedLearning = false;

    @NotBlank(message = "Image Name is required")
    private String imageName;

    private PublishStatus publishStatus = PublishStatus.UNPUBLISHED;

    private String sourceUrl;

    private Set<FederatedAppTagDTO> tags;
    private String icon;

    private Integer certificationLevel;

    //only internal
    private Long latestUnpublishedVersionId;

    private Long latestVersionId;
    private String latestVersion;

    //OLD FC VERSION
    private Boolean oldFCVersion;

    //rating
    private int count;
    private float average;

    @JsonProperty("hasImage")
    public boolean hasImage() {
        return imageName != null && !imageName.isEmpty();
    }

}

