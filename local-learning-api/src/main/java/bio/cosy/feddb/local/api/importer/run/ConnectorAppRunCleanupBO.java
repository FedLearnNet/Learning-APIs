package bio.cosy.feddb.local.api.importer.run;

import bio.cosy.feddb.core.execution.run.dto.FinishRunDTO;
import bio.cosy.feddb.core.execution.run.dto.UpdateRunDTO;
import bio.cosy.feddb.core.execution.run.types.RunStatusTypes;
import bio.cosy.feddb.local.execution.attempt.ExecutionAttemptLifecycle;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class ConnectorAppRunCleanupBO {

    @Inject
    ExecutionAttemptLifecycle lifecycle;

    public void finishAttempt(Long attemptId) {
        lifecycle.finish(attemptId, new FinishRunDTO(RunStatusTypes.FINISHED));
    }

    public void failAttempt(Long attemptId, String error) {
        lifecycle.updateTransactional(attemptId, new UpdateRunDTO(attemptId, RunStatusTypes.ERROR, error));
    }
}
