package bio.cosy.feddb.local.api.cohort.patient.tools;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class PatientToolRunFileAO implements PanacheRepository<PatientToolRunFileEntity> {

    public Optional<PatientToolRunFileEntity> findByRunId(Long runId) {
        return find("run.id = ?1", Sort.by("id", Sort.Direction.Descending), runId).firstResultOptional();
    }

    public List<PatientToolRunFileEntity> findByRunIds(List<Long> runIds) {
        return list("run.id in ?1", runIds);
    }

    public long deleteByRunIds(List<Long> runIds) {
        return delete("run.id in ?1", runIds);
    }
}
