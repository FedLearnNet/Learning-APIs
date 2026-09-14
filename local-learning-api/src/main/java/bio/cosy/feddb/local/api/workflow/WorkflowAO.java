package bio.cosy.feddb.local.api.workflow;

import bio.cosy.feddb.local.config.FLNetClientConfig;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class WorkflowAO implements PanacheRepository<WorkflowEntity> {

    @Inject
    FLNetClientConfig config;

    public java.util.Optional<WorkflowEntity> findByUserIdAndAppId(Long workflowId, String keycloakId) {
        return find("id = ?1 and ( keycloakId = ?2 or keycloakId = ?3)", workflowId, keycloakId, config.user().systemUserName()).firstResultOptional();
    }

}
