package bio.cosy.feddb.local.api.learning.request;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import io.quarkus.panache.common.Page;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;

import java.util.*;

@ApplicationScoped
public class FederatedLearningRequestAO implements PanacheRepository<FederatedLearningRequestEntity> {

    public List<FederatedLearningRequestEntity> list(
            Page page, FederatedLearningRequestStatus status) {

        StringBuilder queryBuilder = new StringBuilder();
        Map<String, Object> parameters = new HashMap<>();

        if (status != null) {
            queryBuilder.append("status  = :status");
            parameters.put("status", status);
        }

        return find(queryBuilder.toString(), parameters).page(page).list();
    }

    public List<FederatedLearningRequestEntity> findApprovedAndRunning() {
        return find("status = ?1 or status = ?2 ORDER BY id DESC",
                FederatedLearningRequestStatus.APPROVED, FederatedLearningRequestStatus.RUNNING).list();
    }

    /**
     * Locks the request so concurrent cohort decisions cannot both finalize it.
     */
    public Optional<FederatedLearningRequestEntity> findByIdForUpdate(Long id) {
        return find("id = ?1", id)
                .withLock(LockModeType.PESSIMISTIC_WRITE)
                .firstResultOptional();
    }

    public Optional<FederatedLearningRequestEntity> findByGlobalFLExperimentUniqueId(String globalFLExperimentUniqueId) {
        return find("globalFLExperimentUniqueId", globalFLExperimentUniqueId).firstResultOptional();
    }

    @Transactional
    public boolean updateStatusTransactional(String globalFLExperimentUniqueId, FederatedLearningRequestStatus status) {
        int updated = update(
                """
                        status = ?1,
                        updatedAt = ?2
                        where globalFLExperimentUniqueId = ?3
                        """,
                status,
                new Date(),
                globalFLExperimentUniqueId
        );
        if (updated > 1) {
            throw new IllegalStateException("Multiple requests updated for global request id " + globalFLExperimentUniqueId);
        }
        return updated == 1;
    }
}
