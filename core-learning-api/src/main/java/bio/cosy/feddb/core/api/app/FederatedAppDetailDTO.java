package bio.cosy.feddb.core.api.app;

import bio.cosy.feddb.core.api.app.config.ToolConfigsDTO;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;
import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
public class FederatedAppDetailDTO extends FederatedAppDTO {

    //workflow
    private int addedToWorkflowCount;
    private Date lastAddedToWorkflow;

    private ToolConfigsDTO appConfig;
    private List<FederatedAppAuthorDTO> authors;
    private List<FederatedAppVersionDTO> versions;

    @JsonIgnore
    public boolean toolYmlIsEqualTo(FederatedAppDetailDTO other) {
        if (other == null) return false;
        if (this.getName() == null && other.getName() != null) return false;
        if (this.getName() != null && !this.getName().equals(other.getName())) return false;
        if (this.getShortDescription() == null && other.getShortDescription() != null) return false;
        if (this.getShortDescription() != null && !this.getShortDescription().equals(other.getShortDescription()))
            return false;
        if (this.getLongDescription() == null && other.getLongDescription() != null) return false;
        if (this.getLongDescription() != null && !this.getLongDescription().equals(other.getLongDescription()))
            return false;
        if (this.getSlug() == null && other.getSlug() != null) return false;
        if (this.getSlug() != null && !this.getSlug().equals(other.getSlug())) return false;
        if (this.getType() == null && other.getType() != null) return false;
        if (this.getType() != null && !this.getType().equals(other.getType())) return false;
        if (this.getSourceUrl() == null && other.getSourceUrl() != null) return false;
        if (this.getSourceUrl() != null && !this.getSourceUrl().equals(other.getSourceUrl())) return false;
        if (this.appConfig == null && other.appConfig != null) return false;
        if (this.appConfig != null && !this.appConfig.equals(other.appConfig)) return false;
        return true;
    }
}

