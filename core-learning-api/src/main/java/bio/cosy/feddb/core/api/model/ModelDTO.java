package bio.cosy.feddb.core.api.model;

import bio.cosy.feddb.core.api.app.FederatedAppDetailDTO;
import bio.cosy.feddb.core.base.BaseDTO;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.UUID;


@EqualsAndHashCode(callSuper = true)
@Data
public class ModelDTO extends BaseDTO {

    @NotBlank(message = "name is mandatory")
    private String name;

    private UUID uniqueModelId;

    @NotBlank(message = "shortDescription is mandatory")
    private String shortDescription;

    @NotBlank(message = "longDescription is mandatory")
    private String longDescription;

    @NotNull(message = "publishStatus is mandatory")
    private ModelPublishStatus publishStatus = ModelPublishStatus.PRIVATE;

    private ModelVersionDTO lastVersion;

    @NotNull(message = "Federated app id is mandatory")
    private Long federatedAppId;
    private Long federatedAppVersionId;

    private FederatedAppDetailDTO federatedApp;

}

