package bio.cosy.feddb.local.api.learning.project;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import io.quarkus.panache.common.Page;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.util.Date;

import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class FederatedLearningProjectAO implements PanacheRepository<FederatedLearningProjectEntity> {

    public Optional<FederatedLearningProjectEntity> findByRequestIdOptional(String id) {
        return find("request.globalFLExperimentUniqueId", id).firstResultOptional();
    }

    public List<FederatedLearningProjectEntity> list(Page page) {
        return find("experiment IS NOT NULL ORDER BY id DESC").page(page).list();
    }

    @Transactional
    public boolean updateCoordinatorTransactional(Long id, boolean isCoordinator) {
        int updated = update(
                """
                        isCoordinator = ?1,
                        updatedAt = ?2
                        where id = ?3
                        """,
                isCoordinator,
                new Date(),
                id
        );
        if (updated > 1) {
            throw new IllegalStateException("Multiple projects updated for id " + id);
        }
        return updated == 1;
    }

}
