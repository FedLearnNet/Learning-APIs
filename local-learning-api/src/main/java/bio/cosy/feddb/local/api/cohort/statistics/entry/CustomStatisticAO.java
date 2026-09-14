package bio.cosy.feddb.local.api.cohort.statistics.entry;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

@ApplicationScoped
public class CustomStatisticAO implements PanacheRepository<CustomStatisticEntity> {

    public List<CustomStatisticEntity> listByDashboard(Long dashboardId) {
        return list("dashboard.id = ?1 order by sortOrder asc, id asc", dashboardId);
    }

    public long deleteByDashboard(Long dashboardId) {
        return delete("dashboard.id = ?1", dashboardId);
    }
}
