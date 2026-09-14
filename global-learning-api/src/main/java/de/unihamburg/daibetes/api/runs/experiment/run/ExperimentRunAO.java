package de.unihamburg.daibetes.api.runs.experiment.run;

import bio.cosy.feddb.core.api.run.RunStatusTypes;
import de.unihamburg.daibetes.api.runs.test.TestRunEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class ExperimentRunAO implements PanacheRepository<ExperimentRunEntity> {

    public List<ExperimentRunEntity> findByExperimentId(Long id) {
        return list("experiment.id = ?1",
                Sort.by("createdAt", Sort.Direction.Descending),
                id);

    }

    public Optional<ExperimentRunEntity> findNextByAppId(Long id) {
        return find("experiment.federatedAppVersion.federatedApp.id = ?1 and status = ?2",
                Sort.by("createdAt", Sort.Direction.Descending),
                id, RunStatusTypes.PENDING).firstResultOptional();

    }
}
