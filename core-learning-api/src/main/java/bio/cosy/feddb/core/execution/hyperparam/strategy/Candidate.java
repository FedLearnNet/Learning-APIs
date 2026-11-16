package bio.cosy.feddb.core.execution.hyperparam.strategy;

import lombok.Data;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One parameter set of a search. The trial index identifies it within the step, so retries of the
 * same trial run the same parameters.
 */
@Data
public class Candidate {

    private int trialIndex;
    private Map<String, Object> params;

    public Candidate(int trialIndex, Map<String, Object> params) {
        if (trialIndex < 0) {
            throw new IllegalArgumentException("trialIndex must not be negative, was " + trialIndex);
        }
        this.trialIndex = trialIndex;
        // Null values stay allowed, an unset hyper parameter is passed on as such.
        this.params = Collections.unmodifiableMap(new LinkedHashMap<>(params));
    }
}
