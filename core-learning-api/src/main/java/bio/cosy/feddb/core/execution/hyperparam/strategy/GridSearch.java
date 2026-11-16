package bio.cosy.feddb.core.execution.hyperparam.strategy;

import bio.cosy.feddb.core.execution.hyperparam.config.HyperParamSearchDTO;
import bio.cosy.feddb.core.execution.hyperparam.config.ParamDomainDTO;

import java.util.*;
import java.util.stream.LongStream;

/**
 * Every combination of a finite space
 */
public final class GridSearch extends SearchStrategy {

    @Override
    protected List<Map<String, Object>> parameterSets(HyperParamSearchDTO search) {
        if (!search.isGridSearchable()) {
            throw new IllegalArgumentException("Grid cannot be enumerated within " + HyperParamSearchDTO.MAX_TRIALS + " trials");
        }
        SortedMap<String, ParamDomainDTO> space = sorted(search);
        return LongStream.range(0, search.combinations().orElseThrow())
                .mapToObj(index -> combinationAt(space, index))
                .toList();
    }
}
