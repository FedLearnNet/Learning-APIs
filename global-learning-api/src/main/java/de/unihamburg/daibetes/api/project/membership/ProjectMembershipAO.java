package de.unihamburg.daibetes.api.project.membership;

import de.unihamburg.daibetes.api.project.ProjectEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.NotAllowedException;

import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class ProjectMembershipAO implements PanacheRepository<ProjectMembershipEntity> {

    public Optional<ProjectMembershipEntity> getMemberByUser(String keycloakId, Long projectId) {
        return find("keycloakId = ?1 and project.id = ?2", keycloakId, projectId).firstResultOptional();
    }

    public boolean isUserIsMember(String keycloakId, ProjectEntity project) {
        return find("keycloakId = ?1 and project = ?2", keycloakId, project).count() > 0;
    }

    public Optional<ProjectMembershipEntity> getByProjectAndUser(Long projectId, String keycloakId) {
        return find("project.id = ?1 and keycloakId = ?2", projectId, keycloakId).firstResultOptional();
    }

    public void checkProjectAndUser(Long projectId, String keycloakId) {
        find("project.id = ?1 and keycloakId = ?2", projectId, keycloakId)
                .firstResultOptional()
                .orElseThrow(() -> new NotAllowedException("User is not a member of the project"));
    }

    public List<ProjectMembershipEntity> getAllByUser(String keycloakId) {
        return list("keycloakId", keycloakId);
    }

}
