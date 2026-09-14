package bio.cosy.feddb.local.api.cohort.patient.tools.log;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

@ApplicationScoped
public class PatientToolRunLogAO implements PanacheRepository<PatientToolRunLogEntity> {


    public List<PatientToolRunLogEntity> findByRunId(Long id) {
        return list("run.id = ?1",
                Sort.by("createdAt", Sort.Direction.Descending),
                id);
    }

    public long deleteByRunIds(List<Long> runIds) {
        return delete("run.id in ?1", runIds);
    }

    public List<PatientToolRunLogEntity> findByRunIdAfter(Long id, Long afterId) {
        return list("run.id = ?1 and id > ?2",
                Sort.by("id", Sort.Direction.Ascending),
                id, afterId == null ? 0L : afterId);
    }

}
