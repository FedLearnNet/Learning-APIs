package bio.cosy.feddb.local.api.learning.project.run;

import bio.cosy.feddb.core.execution.event.ExecutionEventPublisher;
import bio.cosy.feddb.core.execution.failure.ExecutionFailure;
import bio.cosy.feddb.core.execution.run.types.RunStatusTypes;
import bio.cosy.feddb.local.api.learning.request.LearningRequestBO;
import bio.cosy.feddb.local.execution.run.ExecutionRunEntity;
import bio.cosy.feddb.local.execution.run.WorkflowExecutionBO;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class LearningCleanupBO{

    @Inject
    WorkflowExecutionBO workflowExecutionBO;

    @Inject
    ExecutionEventPublisher events;

    @Inject
    LearningRequestBO requestBO;

    public void stopTraining(ExecutionRunEntity run) {
        workflowExecutionBO.stopTransactional(run.getId());
    }

    public void finishTraining(ExecutionRunEntity run, String globalId) {
        finishRun(run, RunStatusTypes.FINISHED, null);
        requestBO.finishLearning(globalId);
    }

    public void failTraining(ExecutionRunEntity run, ExecutionFailure failure, String globalId) {
        finishRun(run, RunStatusTypes.ERROR, failure);
        requestBO.stopLearning(globalId);
    }


    private void finishRun(ExecutionRunEntity run, RunStatusTypes status, ExecutionFailure failure) {
        if (run.hasEnded()) {
            return;
        }
        run.end(status, failure);
        events.runFinished(run.getId(), status);
    }
}
