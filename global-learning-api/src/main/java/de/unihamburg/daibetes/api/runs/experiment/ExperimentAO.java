package de.unihamburg.daibetes.api.runs.experiment;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

@ApplicationScoped
public class ExperimentAO implements PanacheRepository<ExperimentEntity> {

    public List<ExperimentEntity> findByAppId(Long id) {
        return list("federatedAppVersion.federatedApp.id = ?1",
                Sort.by("createdAt", Sort.Direction.Descending),
                id);

    }

}
