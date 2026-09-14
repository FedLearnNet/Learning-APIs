package de.unihamburg.daibetes.api.workflow;

import bio.cosy.feddb.core.api.app.PublishStatus;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class WorkflowAO implements PanacheRepository<WorkflowEntity> {

    public Optional<WorkflowEntity> findByUserIdAndWorkflowId(Long workflowId, String keycloakId) {
        return find("id = ?1 and keycloakId = ?2", workflowId, keycloakId).firstResultOptional();
    }

    public Optional<WorkflowEntity> findEmptyByUserId(String keycloakId) {
        return find(
                "keycloakId = ?1 " +
                "and (inputs is null or function('json_array_length', inputs) = 0) " +
                "and nodes is empty " +
                "and connections is empty",
                keycloakId
        ).firstResultOptional();
    }

    public List<WorkflowEntity> listWorkflows(String keycloakId) {
        return find("publishStatus = ?1 or keycloakId = ?2", PublishStatus.PUBLISHED, keycloakId).list();
    }

    public List<WorkflowEntity> listWorkflowForApp(Long appId, String keycloakId) {
        return find(
                "select distinct w from WorkflowEntity w " +
                        "join WorkflowNodeEntity n on n.workflow = w " +
                        "where ( w.publishStatus = ?1 or w.keycloakId = ?2 ) and n.federatedAppVersion.federatedApp.id = ?3",
                PublishStatus.PUBLISHED, keycloakId, appId
        ).list();
    }
}
