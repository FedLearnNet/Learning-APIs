package bio.cosy.feddb.core.api.project;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;
//TODO REMOVE DONT ADD SOMETHING ADD THE MOMENT
@EqualsAndHashCode(callSuper = true)
@Data
public class ProjectDetailDTO extends ProjectDTO {

    private Date verifiedOn;

    private int certificationLevel;

    private boolean isAudited = false;

    //Will be automatically set at start learning process (can not be changed by the user)
    @JsonProperty("isCoordinator")
    private Boolean isCoordinator;

    @JsonIgnore
    public boolean isCoordinator() {
        return isCoordinator != null && isCoordinator;
    }


    public String getRole() {
        if (this.getIsCoordinator() == null) {
            return super.getRole();
        }
        if (this.getIsCoordinator()) {
            return ProjectRole.COORDINATOR.toString();
        } else {
            return ProjectRole.PARTICIPANT.toString();
        }
    }

}
