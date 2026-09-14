package bio.cosy.feddb.core.api.store;

import bio.cosy.feddb.core.api.app.FederatedAppTagDTO;
import bio.cosy.feddb.core.api.app.FederatedAppType;
import bio.cosy.feddb.core.api.app.PublishStatus;
import bio.cosy.feddb.core.api.app.config.ToolConfigsDTO;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.util.Set;

@Data
public class StoreRatingCreateDTO {

    @NotBlank(message = "Name is required")
    private String name;

    @NotBlank(message = "Image Name is required")
    private String imageName;

    @NotBlank(message = "Slug is required")
    @Pattern(regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$", message = "Slug should contain only letters and digits separated by a hyphen.")
    private String slug;
    private String shortDescription;
    private String longDescription;

    private FederatedAppType type;
    private PublishStatus publishStatus;
    private String sourceUrl;
    private String icon;

    private Set<FederatedAppTagDTO> tags;

    private ToolConfigsDTO appConfig;


}

