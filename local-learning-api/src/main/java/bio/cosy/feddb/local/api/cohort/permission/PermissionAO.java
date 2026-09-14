package bio.cosy.feddb.local.api.cohort.permission;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Set;

@ApplicationScoped
public class PermissionAO implements PanacheRepository<PermissionEntity> {

    public List<PermissionEntity> isAllowedToQuery(String userId) {
        return find("isAllowedToQuery = true " +
                "AND (userId = ?1 OR userId IS NULL) " +
                "AND (validFrom IS NULL OR validFrom <= CURRENT_DATE) " +
                "AND (validUntil IS NULL OR validUntil >= CURRENT_DATE)",
                userId).list();
    }

    public List<PermissionEntity> getActivePermissionsForCohorts(String userId, Set<Long> cohortIds) {
        return find("(userId = ?1 OR userId IS NULL) " +
                "AND cohort.id IN ?2 " +
                "AND (validFrom IS NULL OR validFrom <= CURRENT_DATE) " +
                "AND (validUntil IS NULL OR validUntil >= CURRENT_DATE)",
                userId, cohortIds).list();
    }

    public List<PermissionEntity> getActivePermissionsForCohort(String userId, Long cohortId) {
        return find("(userId = ?1 OR userId IS NULL) " +
                        "AND cohort.id = ?2 " +
                        "AND (validFrom IS NULL OR validFrom <= CURRENT_DATE) " +
                        "AND (validUntil IS NULL OR validUntil >= CURRENT_DATE)",
                userId, cohortId).list();
    }

}
