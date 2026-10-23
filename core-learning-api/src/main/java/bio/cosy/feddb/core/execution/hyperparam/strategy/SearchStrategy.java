package bio.cosy.feddb.core.execution.hyperparam.strategy;

import bio.cosy.feddb.core.execution.hyperparam.config.HyperParamSearchDTO;
import bio.cosy.feddb.core.execution.hyperparam.config.ParamDomainDTO;
import bio.cosy.feddb.core.execution.hyperparam.config.SearchStrategyType;

import java.util.*;
import java.util.stream.IntStream;

/**
 * Turns a search into its trials (so one step, can with the attempts turn into a trial)
 */
public abstract class SearchStrategy {

    /** Parameter sets in trial order. */
    protected abstract List<Map<String, Object>> parameterSets(HyperParamSearchDTO search);

    public final List<Candidate> trials(HyperParamSearchDTO search) {
        List<Map<String, Object>> sets = parameterSets(search);
        return IntStream.range(0, sets.size()).mapToObj(i -> new Candidate(i, sets.get(i))).toList();
    }

    public static SearchStrategy of(SearchStrategyType type) {
        return switch (type) {
            case GRID -> new GridSearch();
            case RANDOM -> new RandomSearch();
        };
    }

    protected static SortedMap<String, ParamDomainDTO> sorted(HyperParamSearchDTO search) {
        return new TreeMap<>(search.getSpace());
    }

    protected static Map<String, Object> combinationAt(SortedMap<String, ParamDomainDTO> space, long index) {
        List<Map.Entry<String, ParamDomainDTO>> params = new ArrayList<>(space.entrySet());
        Object[] values = new Object[params.size()];
        long remaining = index;
        for (int i = params.size() - 1; i >= 0; i--) {
            ParamDomainDTO domain = params.get(i).getValue();
            long size = domain.size().orElseThrow();
            values[i] = domain.valueAt(remaining % size);
            remaining /= size;
        }
        Map<String, Object> combination = new LinkedHashMap<>();
        for (int i = 0; i < params.size(); i++) {
            combination.put(params.get(i).getKey(), values[i]);
        }
        return combination;
    }
}
