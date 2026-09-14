package de.unihamburg.daibetes.api.analysis.chat.hitl;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Optional;


@ApplicationScoped
public class HumanInTheLoopRequestAO implements PanacheRepository<HumanInTheLoopRequestEntity> {

    public Optional<HumanInTheLoopRequestEntity> findPendingByWorkflow(Long workflowId, String keycloakId) {
        return find("message.dataAnalysis.id = ?1 AND message.dataAnalysis.keycloakId = ?2 AND isAnswered = false",
                workflowId, keycloakId).firstResultOptional();
    }
}
