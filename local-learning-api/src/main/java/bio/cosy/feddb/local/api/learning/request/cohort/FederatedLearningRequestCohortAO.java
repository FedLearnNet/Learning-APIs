package bio.cosy.feddb.local.api.learning.request.cohort;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@ApplicationScoped
public class FederatedLearningRequestCohortAO
        implements PanacheRepository<FederatedLearningRequestCohortEntity> {

    public List<FederatedLearningRequestCohortEntity> findByRequest(Long requestId) {
        return find("request.id = ?1 order by cohort.id", requestId).list();
    }

    public Set<Long> findCohortIdsByRequest(Long requestId) {
        return findByRequest(requestId).stream()
                .map(decision -> decision.getCohort().getId())
                .collect(Collectors.toSet());
    }

    public boolean hasPending(Long requestId) {
        return count("request.id = ?1 and status = ?2",
                requestId, FederatedLearningRequestCohortStatus.PENDING) > 0;
    }

    public boolean hasApproved(Long requestId) {
        return count("request.id = ?1 and status = ?2",
                requestId, FederatedLearningRequestCohortStatus.APPROVED) > 0;
    }

    public int decidePending(Long requestId,
                             Collection<Long> cohortIds,
                             FederatedLearningRequestCohortStatus status) {
        if (cohortIds == null || cohortIds.isEmpty()) {
            return 0;
        }
        return update("""
                        status = ?1,
                        updatedAt = ?2,
                        version = version + 1
                        where request.id = ?3
                          and cohort.id in ?4
                          and status = ?5
                        """,
                status,
                new Date(),
                requestId,
                cohortIds,
                FederatedLearningRequestCohortStatus.PENDING
        );
    }
}
