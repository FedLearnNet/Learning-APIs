package bio.cosy.feddb.core.execution.hyperparam.planner;

import bio.cosy.feddb.core.execution.attempt.BaseExecutionAttemptDTO;
import bio.cosy.feddb.core.execution.hyperparam.strategy.Candidate;
import bio.cosy.feddb.core.execution.run.types.RunStatusTypes;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.Objects;

/** What a step does next with its search. */
@Data
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SearchPlan {

    /**
     * RUNNING while a trial runs or is about to start, FINISHED once every trial ended and one
     * finished, ERROR when none finished.
     */
    private RunStatusTypes status;

    /** Trial to create an attempt for and start; null while one runs or once the search is done. */
    private Candidate next;

    /** Attempt whose outputs the step hands on, only set once finished. */
    private BaseExecutionAttemptDTO selected;

    static SearchPlan waiting() {
        return new SearchPlan(RunStatusTypes.RUNNING, null, null);
    }

    static SearchPlan start(Candidate next) {
        return new SearchPlan(RunStatusTypes.RUNNING, Objects.requireNonNull(next, "next"), null);
    }

    static SearchPlan finished(BaseExecutionAttemptDTO selected) {
        return new SearchPlan(RunStatusTypes.FINISHED, null, Objects.requireNonNull(selected, "selected"));
    }

    static SearchPlan failed() {
        return new SearchPlan(RunStatusTypes.ERROR, null, null);
    }

    public boolean isDone() {
        return RunStatusTypes.isFinalStatus(status);
    }
}
