package bio.cosy.feddb.core.api.app;

import bio.cosy.feddb.core.api.app.config.ToolConfigsDTO;
import bio.cosy.feddb.core.base.BaseDTO;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
public class FederatedAppVersionDTO extends BaseDTO {

    @PositiveOrZero(message = "Federated App is required")
    private Long federatedAppId;

    @NotBlank(message = "Version is required")
    private String appVersion;

    private String publishHash;
    private AppPublishInfoDTO publishInfo;

    private String changelog;

    private PublishStatus versionPublishStatus = PublishStatus.UNPUBLISHED;
    private Boolean needsInternetAccess = true;
    private Boolean needsHostAccess = false;


    private int certificationLevel;

    private String imageName;
    private String shortDescription;
    private String longDescription;

    private ToolConfigsDTO appConfig;

    private List<ToolAuditDTO> audits;
}
