package bio.cosy.feddb.local.api.cohort.inclusion;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class CohortCriterionAO implements PanacheRepository<CohortCriterionEntity> {
}
