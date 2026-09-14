package bio.cosy.feddb.core.api.workflow.node;

import bio.cosy.feddb.core.api.app.FederatedAppDetailDTO;
import bio.cosy.feddb.core.api.app.FederatedAppVersionDTO;
import bio.cosy.feddb.core.api.app.config.ToolInputConfigDTO;
import bio.cosy.feddb.core.api.model.ModelDetailDTO;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

@EqualsAndHashCode(callSuper = true)
@Data
@JsonIgnoreProperties(value = {"appInputConfig"},
        ignoreUnknown = true)
public class WorkflowNodeDetailDTO extends WorkflowNodeDTO {
    private FederatedAppDetailDTO appDetail;
    private ModelDetailDTO modelDetail;

    public List<ToolInputConfigDTO> getAppInputConfig() {
        return appDetail.getAppConfig().getInput();
    }

    @Override
    public boolean isOldFCVersion() {
        if (appDetail == null) {
            return super.isOldFCVersion();
        }
        final Boolean isOldFCVersion = appDetail.getOldFCVersion();
        return isOldFCVersion != null && isOldFCVersion;
    }

    public boolean needsInternetAccess() {
        return resolveAccessFlag(FederatedAppVersionDTO::getNeedsInternetAccess,
                FederatedAppDetailDTO::getNeedsInternetAccess);
    }


    public boolean needsHostAccess() {
        return resolveAccessFlag(FederatedAppVersionDTO::getNeedsHostAccess,
                FederatedAppDetailDTO::getNeedsHostAccess);
    }


    public boolean needsFederatedLearningAccess() {
        if (Boolean.TRUE.equals(getSupportsFederatedLearning())) {
            return true;
        }
        return appDetail != null && Boolean.TRUE.equals(appDetail.getSupportsFederatedLearning());
    }

    private boolean resolveAccessFlag(Function<FederatedAppVersionDTO, Boolean> fromVersion,
                                      Function<FederatedAppDetailDTO, Boolean> fromApp) {
        return pinnedVersion()
                .map(fromVersion)
                .or(() -> Optional.ofNullable(appDetail).map(fromApp))
                .orElse(Boolean.FALSE);
    }


    private Optional<FederatedAppVersionDTO> pinnedVersion() {
        if (appDetail == null || appDetail.getVersions() == null || getFederatedAppVersionId() == null) {
            return Optional.empty();
        }
        return appDetail.getVersions().stream()
                .filter(Objects::nonNull)
                .filter(version -> getFederatedAppVersionId().equals(version.getId()))
                .findFirst();
    }

}
