package bio.cosy.feddb.local.api.cohort;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class CohortAO implements PanacheRepository<CohortEntity> {
    public Optional<CohortEntity> findByName(String name) {
        // The name is unique, so we can use findFirst and treat it as an ID
        return find("name", name).firstResultOptional();
    }

    public boolean existsByNameExcludingId(String name, Long excludeCohortId) {
        String normalized = normalizeName(name);
        if (excludeCohortId == null) {
            return count("LOWER(name) = ?1", normalized) > 0;
        }
        return count("LOWER(name) = ?1 and id <> ?2", normalized, excludeCohortId) > 0;
    }

    public List<CohortEntity> listExternal() {
        return list("treSourceId is not null and (status is null or status <> ?1)", PublicationStatus.RETIRED);
    }

    public Optional<CohortEntity> findByTreSourceId(String treSourceId) {
        return find("treSourceId", treSourceId).firstResultOptional();
    }

    private static String normalizeName(String name) {
        return name == null ? "" : name.trim().toLowerCase();
    }
}
