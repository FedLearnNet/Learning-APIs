package bio.cosy.feddb.core.execution.hyperparam.strategy;

import bio.cosy.feddb.core.execution.hyperparam.config.HyperParamSearchDTO;
import bio.cosy.feddb.core.execution.hyperparam.config.ParamDomainDTO;

import java.util.*;
import java.util.random.RandomGenerator;
import java.util.random.RandomGeneratorFactory;

/**
 * A fixed number of seeded draws. A finite space yields distinct combinations and never more than
 * it has; a continuous or astronomically large one is sampled per parameter, so repeats are possible.
 */
public final class RandomSearch extends SearchStrategy {

    private static final String ALGORITHM = "L64X128MixRandom";

    private static final long SEED = 42L;

    @Override
    protected List<Map<String, Object>> parameterSets(HyperParamSearchDTO search) {
        SortedMap<String, ParamDomainDTO> space = sorted(search);
        RandomGenerator random = RandomGeneratorFactory.of(ALGORITHM).create(SEED);
        OptionalLong combinations = search.combinations();
        if (combinations.isPresent()) {
            return withoutReplacement(space, search.getIterations(), combinations.getAsLong(), random);
        }
        return withReplacement(space, search.getIterations(), random);
    }

    private static List<Map<String, Object>> withoutReplacement(SortedMap<String, ParamDomainDTO> space,
                                                                int iterations, long size, RandomGenerator random) {
        int count = (int) Math.min(iterations, size);
        // Floyd's algorithm: distinct indices without materialising a grid that may be huge.
        Set<Long> picked = new LinkedHashSet<>();
        for (long candidate = size - count; candidate < size; candidate++) {
            long index = random.nextLong(candidate + 1);
            picked.add(picked.contains(index) ? candidate : index);
        }
        // Floyd's insertion order favours high indices; shuffled so early trials are not biased.
        List<Long> indices = new ArrayList<>(picked);
        Collections.shuffle(indices, random);
        return indices.stream().map(index -> combinationAt(space, index)).toList();
    }

    private static List<Map<String, Object>> withReplacement(SortedMap<String, ParamDomainDTO> space,
                                                             int iterations, RandomGenerator random) {
        List<Map<String, Object>> draws = new ArrayList<>();
        for (int i = 0; i < iterations; i++) {
            Map<String, Object> draw = new LinkedHashMap<>();
            space.forEach((name, domain) -> draw.put(name, domain.sample(random)));
            draws.add(draw);
        }
        return draws;
    }
}
