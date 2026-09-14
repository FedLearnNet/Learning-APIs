package de.unihamburg.daibetes.api.project.membership;

import bio.cosy.feddb.core.base.BaseAuthEntity;
import bio.cosy.feddb.core.base.BaseEntity;
import de.unihamburg.daibetes.api.project.ProjectEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@Entity(name = "ProjectMembershipEntity")
@Table(name = "project_memberships",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"project_id", "keycloak_id"})
        })
public class ProjectMembershipEntity extends BaseAuthEntity {

    @ManyToOne(cascade = CascadeType.REFRESH)
    @JoinColumn(name = "project_id", nullable = false)
    private ProjectEntity project;

}
