package de.unihamburg.daibetes.api.runs.experiment.message;

import bio.cosy.feddb.core.api.run.message.RunMessageTypes;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

@ApplicationScoped
public class ExperimentRunMessageAO implements PanacheRepository<ExperimentRunMessageEntity> {

    public List<ExperimentRunMessageEntity> findByRun(Long id) {
        return list("run.id", id);
    }

    public List<ExperimentRunMessageEntity> findByRun(Long id, RunMessageTypes type) {
        return list("run.id = ?1 and type = ?2",
                Sort.by("createdAt", Sort.Direction.Descending),
                id, type);
    }

    public List<ExperimentRunMessageEntity> findByExperiment(Long experimentId, RunMessageTypes type) {
        return list("run.experiment.id = ?1 and type = ?2",
                Sort.by("createdAt", Sort.Direction.Descending),
                experimentId, type);
    }


}
