package bio.cosy.feddb.local.api.cohort.patient.tools;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

@ApplicationScoped
public class PatientToolRunAO implements PanacheRepository<PatientToolRunEntity> {

    public List<PatientToolRunEntity> findByCohort(Long cohortId, PatientToolRunType toolType, int limit) {
        return find("cohort.id = ?1 and toolType = ?2",
                Sort.by("startedAt", Sort.Direction.Descending).and("id", Sort.Direction.Descending),
                cohortId, toolType)
                .page(0, limit)
                .list();
    }

    public List<PatientToolRunEntity> findAllByCohort(Long cohortId) {
        return list("cohort.id", cohortId);
    }

    public long deleteByIds(List<Long> ids) {
        return delete("id in ?1", ids);
    }
}
