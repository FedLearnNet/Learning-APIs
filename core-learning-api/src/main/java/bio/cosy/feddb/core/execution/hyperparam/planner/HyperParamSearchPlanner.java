package bio.cosy.feddb.core.execution.hyperparam.planner;

import bio.cosy.feddb.core.execution.attempt.BaseExecutionAttemptDTO;
import bio.cosy.feddb.core.execution.hyperparam.config.HyperParamSearchDTO;
import bio.cosy.feddb.core.execution.hyperparam.config.OptimizationDirection;
import bio.cosy.feddb.core.execution.hyperparam.strategy.Candidate;
import bio.cosy.feddb.core.execution.hyperparam.strategy.SearchStrategy;
import bio.cosy.feddb.core.execution.message.ExecutionMessageMetricDTO;
import bio.cosy.feddb.core.execution.run.types.RunStatusTypes;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Decides the next move of a step's search from the attempts it has so far.
 *
 * <p>Trials run one at a time and a failed trial is not retried. Holds no state: the attempts are
 * the search history, so the same call works on the first start, after every finished attempt and
 * after a restart. Callers apply the plan while holding the step's lock, so a duplicate finish
 * event cannot start the same trial twice.</p>
 *
 * <p>Mirrors scikit-learn's {@code BaseSearchCV}: a failed trial is skipped when ranking (its
 * {@code error_score} is NaN) and a missing or NaN score ranks last. Ties go to the lower trial.</p>
 *
 * <p>A CDI bean so Quarkus validates the search on every call; an invalid one fails with a
 * {@code ConstraintViolationException} before any trial is planned.</p>
 */
@ApplicationScoped
public class HyperParamSearchPlanner {

    public SearchPlan plan(@NotNull @Valid HyperParamSearchDTO search,
                           @NotNull Collection<? extends BaseExecutionAttemptDTO> attempts) {
        if (attempts.stream().anyMatch(attempt -> !RunStatusTypes.isFinalStatus(attempt.getStatus()))) {
            return SearchPlan.waiting();
        }
        Set<Integer> issued = attempts.stream().map(BaseExecutionAttemptDTO::getTrialIndex).collect(Collectors.toSet());
        Optional<Candidate> next = SearchStrategy.of(search.getStrategy()).trials(search).stream()
                .filter(trial -> !issued.contains(trial.getTrialIndex()))
                .findFirst();
        if (next.isPresent()) {
            return SearchPlan.start(next.get());
        }
        // A stopped attempt was cancelled on purpose and, like a failed one, is not ranked.
        List<BaseExecutionAttemptDTO> finished = attempts.stream()
                .filter(attempt -> attempt.getStatus() == RunStatusTypes.FINISHED)
                .map(BaseExecutionAttemptDTO.class::cast)
                .toList();
        return best(search, finished).map(SearchPlan::finished).orElseGet(SearchPlan::failed);
    }

    private static Optional<BaseExecutionAttemptDTO> best(HyperParamSearchDTO search, List<BaseExecutionAttemptDTO> finished) {
        Comparator<BaseExecutionAttemptDTO> byTrial = Comparator.comparingInt(BaseExecutionAttemptDTO::getTrialIndex);
        if (search.getMetric() == null || search.getMetric().isBlank()) {
            return finished.stream().min(byTrial);
        }
        Comparator<Double> scores = search.getDirection() == OptimizationDirection.MAXIMIZE
                ? Comparator.reverseOrder()
                : Comparator.naturalOrder();
        return finished.stream().min(Comparator
                .comparing((BaseExecutionAttemptDTO attempt) -> score(search, attempt), Comparator.nullsLast(scores))
                .thenComparing(byTrial));
    }

    /** Value of the attempt's score message; missing, of another metric, non-numeric or NaN ranks last. */
    private static Double score(HyperParamSearchDTO search, BaseExecutionAttemptDTO attempt) {
        ExecutionMessageMetricDTO metric = attempt.getScoreMetric();
        if (metric == null || metric.getValue() == null || !search.getMetric().equals(metric.getMetric())) {
            return null;
        }
        try {
            double value = Double.parseDouble(metric.getValue().trim());
            return Double.isNaN(value) ? null : value;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
