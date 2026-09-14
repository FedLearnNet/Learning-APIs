package bio.cosy.feddb.local.api.cohort.queryability;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

@ApplicationScoped
public class CohortQueryAbilityAO implements PanacheRepository<CohortQueryAbilityEntity> {

    public List<CohortQueryAbilityEntity> findAll(long cohortId) {
        return find("cohort.id", cohortId).list();
    }

    public void deleteAll(Long cohortId) {
        delete("cohort.id", cohortId);
    }
}
