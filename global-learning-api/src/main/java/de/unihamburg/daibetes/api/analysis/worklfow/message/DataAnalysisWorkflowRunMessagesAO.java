package de.unihamburg.daibetes.api.analysis.worklfow.message;

import bio.cosy.feddb.core.api.run.message.RunMessageTypes;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class DataAnalysisWorkflowRunMessagesAO implements PanacheRepository<DataAnalysisWorkflowRunMessagesEntity> {

    public List<DataAnalysisWorkflowRunMessagesEntity> findByStep(Long id) {
        return list("step.id = ?1 and type = ?2",
                Sort.by("createdAt", Sort.Direction.Descending),
                id, RunMessageTypes.LOG);
    }

    public Optional<DataAnalysisWorkflowRunMessagesEntity> findLastLogByStep(Long id) {
        return find("step.id = ?1 and type = ?2 order by id desc",
                id, RunMessageTypes.LOG)
                .firstResultOptional();
    }

    public Optional<DataAnalysisWorkflowRunMessagesEntity> findLastLogByPrediction(Long id) {
        return find("step.id = ?1 and type = ?2 order by id desc",
                id, RunMessageTypes.LOG)
                .firstResultOptional();
    }

}
