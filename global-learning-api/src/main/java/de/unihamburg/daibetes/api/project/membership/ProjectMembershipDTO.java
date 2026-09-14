package de.unihamburg.daibetes.api.project.membership;

import bio.cosy.feddb.core.base.BaseAuthDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

@EqualsAndHashCode(callSuper = true)
@Data
public class ProjectMembershipDTO extends BaseAuthDTO {

    private Long projectId;

    public ProjectMembershipDTO(String keycloakId, Long projectId) {
        this.setKeycloakId(keycloakId);
        this.projectId = projectId;
    }

}
