package de.unihamburg.daibetes.api.app.audit;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class ToolAuditAO implements PanacheRepository<ToolAuditEntity> {

    public Optional<ToolAuditEntity> getByIdAndKeycloakId(Long id, String keycloakId) {
        return find("id = ?1 and keycloakId = ?2", id, keycloakId).firstResultOptional();
    }

    public List<ToolAuditEntity> list(Long toolId, String filterKeycloakId) {
        return find("appVersion.id = ?1 and keycloakId = ?2", toolId, filterKeycloakId).list();
    }

    public Optional<ToolAuditEntity> findByToolVersionId(Long toolId, String filterKeycloakId) {
        return find("appVersion.id = ?1 and keycloakId = ?2", toolId, filterKeycloakId).firstResultOptional();
    }

    public List<ToolAuditEntity> list(Long toolId) {
        return find("appVersion.id", toolId).list();
    }

    public List<ToolAuditEntity> list(String filterKeycloakId) {
        return find("keycloakId", filterKeycloakId).list();
    }
}
