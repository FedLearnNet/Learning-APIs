package de.unihamburg.daibetes.execution.attempt;

import bio.cosy.feddb.core.execution.ExecutionPurpose;
import bio.cosy.feddb.core.execution.attempt.BaseExecutionAttemptAO;
import bio.cosy.feddb.core.execution.run.types.RunStatusTypes;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

@ApplicationScoped
public class ExecutionAttemptAO extends BaseExecutionAttemptAO<ExecutionAttemptEntity> {

    /** Attempts of test runs of an app still waiting for its app engine, oldest first. */
    public List<ExecutionAttemptEntity> listPendingTestAttemptsOfApp(Long appId) {
        return list("status = ?1 and step.run.purpose = ?2 and step.federatedAppVersion.federatedApp.id = ?3",
                Sort.by("id"), RunStatusTypes.PENDING, ExecutionPurpose.TEST, appId);
    }
}
