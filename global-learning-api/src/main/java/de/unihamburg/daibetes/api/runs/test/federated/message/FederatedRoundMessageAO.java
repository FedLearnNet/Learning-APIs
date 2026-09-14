package de.unihamburg.daibetes.api.runs.test.federated.message;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

@ApplicationScoped
public class FederatedRoundMessageAO implements PanacheRepository<FederatedRoundMessageEntity> {

    public List<FederatedRoundMessageEntity> findByRun(Long runId) {
        return list("run.id = ?1",
                Sort.by("createdAt", Sort.Direction.Ascending),
                runId);
    }
}
