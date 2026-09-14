package bio.cosy.feddb.local.api.cohort.statistics.dashboard;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

@ApplicationScoped
public class CustomStatisticsDashboardAO implements PanacheRepository<CustomStatisticsDashboardEntity> {

    public List<CustomStatisticsDashboardEntity> listByCohort(Long cohortId) {
        return list("cohort.id = ?1 order by sortOrder asc, id asc", cohortId);
    }
}
