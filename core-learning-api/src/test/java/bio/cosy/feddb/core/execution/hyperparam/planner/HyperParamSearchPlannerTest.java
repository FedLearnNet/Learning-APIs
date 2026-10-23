package bio.cosy.feddb.core.execution.hyperparam.planner;

import bio.cosy.feddb.core.execution.attempt.BaseExecutionAttemptDTO;
import bio.cosy.feddb.core.execution.hyperparam.config.HyperParamSearchDTO;
import bio.cosy.feddb.core.execution.hyperparam.config.OptimizationDirection;
import bio.cosy.feddb.core.execution.hyperparam.config.ParamDomainDTO;
import bio.cosy.feddb.core.execution.message.ExecutionMessageMetricDTO;
import bio.cosy.feddb.core.execution.run.types.RunStatusTypes;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class HyperParamSearchPlannerTest {

    private final HyperParamSearchPlanner planner = new HyperParamSearchPlanner();

    @Test
    void startsTheFirstTrial() {
        SearchPlan plan = planner.plan(grid(4), List.of());

        assertEquals(RunStatusTypes.RUNNING, plan.getStatus());
        assertEquals(0, plan.getNext().getTrialIndex());
    }

    @Test
    void startsTheNextTrialOnceThePreviousEnded() {
        SearchPlan plan = planner.plan(grid(4), List.of(attempt(0, RunStatusTypes.FINISHED, 0.5)));

        assertEquals(1, plan.getNext().getTrialIndex());
    }

    @Test
    void waitsWhileATrialRuns() {
        SearchPlan plan = planner.plan(grid(2), List.of(attempt(0, RunStatusTypes.RUNNING, null)));

        assertEquals(RunStatusTypes.RUNNING, plan.getStatus());
        assertNull(plan.getNext());
    }

    @Test
    void selectsTheBestScoreInTheConfiguredDirection() {
        HyperParamSearchDTO search = grid(3);
        search.setMetric("loss");
        search.setDirection(OptimizationDirection.MINIMIZE);
        List<BaseExecutionAttemptDTO> attempts = List.of(
                attempt(0, RunStatusTypes.FINISHED, 0.4),
                attempt(1, RunStatusTypes.FINISHED, 0.2),
                attempt(2, RunStatusTypes.FINISHED, Double.NaN));

        SearchPlan plan = planner.plan(search, attempts);

        assertEquals(RunStatusTypes.FINISHED, plan.getStatus());
        assertTrue(plan.isDone());
        assertEquals(1, plan.getSelected().getTrialIndex());
    }

    @Test
    void movesOnFromAFailedTrialAndSkipsItWhenRanking() {
        HyperParamSearchDTO search = grid(2);

        assertEquals(1, planner.plan(search, List.of(attempt(0, RunStatusTypes.ERROR, null))).getNext().getTrialIndex());

        List<BaseExecutionAttemptDTO> attempts = List.of(
                attempt(0, RunStatusTypes.ERROR, null),
                attempt(1, RunStatusTypes.FINISHED, null));
        assertEquals(1, planner.plan(search, attempts).getSelected().getTrialIndex());
    }

    @Test
    void failsWhenNoTrialFinished() {
        SearchPlan plan = planner.plan(grid(1), List.of(attempt(0, RunStatusTypes.ERROR, null)));

        assertEquals(RunStatusTypes.ERROR, plan.getStatus());
        assertTrue(plan.isDone());
    }

    @Test
    void stepWithoutSearchRunsOneTrialAndSelectsIt() {
        HyperParamSearchDTO search = HyperParamSearchDTO.fixed(Map.of("epochs", 10));

        SearchPlan start = planner.plan(search, List.of());
        assertEquals(0, start.getNext().getTrialIndex());
        assertEquals(Map.of("epochs", 10), start.getNext().getParams());

        BaseExecutionAttemptDTO only = attempt(0, RunStatusTypes.FINISHED, null);
        assertSame(only, planner.plan(search, List.of(only)).getSelected());
    }

    private static HyperParamSearchDTO grid(int trials) {
        HyperParamSearchDTO search = new HyperParamSearchDTO();
        search.getSpace().put("a", ParamDomainDTO.intRange(0, trials - 1));
        return search;
    }

    @Test
    void ranksOnlyNumericScoresOfTheSearchMetric() {
        HyperParamSearchDTO search = grid(3);
        search.setMetric("loss");
        search.setDirection(OptimizationDirection.MINIMIZE);
        BaseExecutionAttemptDTO otherMetric = attempt(0, RunStatusTypes.FINISHED, 0.1);
        otherMetric.getScoreMetric().setMetric("accuracy");
        List<BaseExecutionAttemptDTO> attempts = List.of(
                otherMetric,
                attempt(1, RunStatusTypes.FINISHED, "n/a"),
                attempt(2, RunStatusTypes.FINISHED, 0.9));

        assertEquals(2, planner.plan(search, attempts).getSelected().getTrialIndex());
    }

    /** An attempt whose score message reports the given value for the "loss" metric. */
    private static BaseExecutionAttemptDTO attempt(int trial, RunStatusTypes status, Object score) {
        BaseExecutionAttemptDTO attempt = new BaseExecutionAttemptDTO();
        attempt.setTrialIndex(trial);
        attempt.setStatus(status);
        if (score != null) {
            ExecutionMessageMetricDTO metric = new ExecutionMessageMetricDTO();
            metric.setMetric("loss");
            metric.setValue(String.valueOf(score));
            attempt.setScoreMetric(metric);
        }
        return attempt;
    }
}
