package de.unihamburg.daibetes.api.project;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

@ApplicationScoped
public class ProjectAO implements PanacheRepository<ProjectEntity> {

    public List<ProjectEntity> getAllByUser(String keycloakId) {
        return find("SELECT p FROM ProjectEntity p JOIN p.memberships m WHERE m.keycloakId = ?1 ORDER BY p.id DESC", keycloakId).list();

    }
}
