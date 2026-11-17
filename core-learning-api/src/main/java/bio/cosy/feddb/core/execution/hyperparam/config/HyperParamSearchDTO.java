package bio.cosy.feddb.core.execution.hyperparam.config;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.OptionalLong;

/**
 * How a step searches its hyper parameters, chosen in the frontend and stored on the step.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class HyperParamSearchDTO {

    /** Upper bound on trials per step; every trial is at least one container. */
    public static final int MAX_TRIALS = 1_000;

    @NotNull
    private SearchStrategyType strategy = SearchStrategyType.GRID;

    @NotNull
    private LinkedHashMap<String, @Valid @NotNull ParamDomainDTO> space = new LinkedHashMap<>();

    /** Trials a random search draws; ignored by a grid search. */
    @Min(1)
    @Max(MAX_TRIALS)
    private int iterations = 10;

    /** Metric reported by the app that ranks trials; without one the first finished trial is selected. */
    private String metric;

    @NotNull
    private OptimizationDirection direction = OptimizationDirection.MAXIMIZE;

    public static HyperParamSearchDTO fixed(Map<String, ?> hyperParams) {
        HyperParamSearchDTO search = new HyperParamSearchDTO();
        if (hyperParams != null) {
            hyperParams.forEach((name, value) -> search.space.put(name, ParamDomainDTO.fixed(value)));
        }
        return search;
    }

    /** Number of parameter sets the space spans, empty when a domain is continuous or the count overflows. */
    public OptionalLong combinations() {
        long combinations = 1;
        for (ParamDomainDTO domain : space.values()) {
            OptionalLong size = domain.size();
            if (size.isEmpty() || size.getAsLong() > Long.MAX_VALUE / combinations) {
                return OptionalLong.empty();
            }
            combinations *= size.getAsLong();
        }
        return OptionalLong.of(combinations);
    }

    @JsonIgnore
    @AssertTrue(message = "A grid search needs fixed, choice or integer range domains with at most "
            + MAX_TRIALS + " combinations; use a random search otherwise")
    public boolean isGridSearchable() {
        if (strategy != SearchStrategyType.GRID || space.values().stream().anyMatch(d -> d == null || !d.isValid())) {
            return true; // Invalid domains are reported on their own.
        }
        OptionalLong combinations = combinations();
        return combinations.isPresent() && combinations.getAsLong() <= MAX_TRIALS;
    }
}
