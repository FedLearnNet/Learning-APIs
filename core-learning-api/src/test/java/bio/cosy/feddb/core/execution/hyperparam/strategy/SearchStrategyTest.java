package bio.cosy.feddb.core.execution.hyperparam.strategy;

import bio.cosy.feddb.core.execution.hyperparam.config.HyperParamSearchDTO;
import bio.cosy.feddb.core.execution.hyperparam.config.ParamDomainDTO;
import bio.cosy.feddb.core.execution.hyperparam.config.SearchStrategyType;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class SearchStrategyTest {

    private static final Validator VALIDATOR = Validation.byDefaultProvider().configure()
            .messageInterpolator(new ParameterMessageInterpolator())
            .buildValidatorFactory()
            .getValidator();

    @Test
    void gridEnumeratesEveryCombinationWithTheLastParameterFastest() {
        HyperParamSearchDTO search = new HyperParamSearchDTO();
        search.getSpace().put("lr", ParamDomainDTO.choice(List.of(0.1, 0.01)));
        search.getSpace().put("depth", ParamDomainDTO.intRange(1, 3));

        List<Candidate> trials = trials(search);

        assertEquals(6, trials.size());
        // Names sort to depth, lr, so lr changes fastest.
        assertEquals(Map.of("depth", 1L, "lr", 0.1), trials.get(0).getParams());
        assertEquals(Map.of("depth", 1L, "lr", 0.01), trials.get(1).getParams());
        assertEquals(Map.of("depth", 3L, "lr", 0.01), trials.get(5).getParams());
    }

    @Test
    void gridDoesNotDependOnInsertionOrder() {
        HyperParamSearchDTO forward = new HyperParamSearchDTO();
        forward.getSpace().put("a", ParamDomainDTO.choice(List.of(1, 2)));
        forward.getSpace().put("b", ParamDomainDTO.choice(List.of("x", "y")));
        HyperParamSearchDTO backward = new HyperParamSearchDTO();
        backward.getSpace().put("b", forward.getSpace().get("b"));
        backward.getSpace().put("a", forward.getSpace().get("a"));

        assertEquals(trials(forward), trials(backward));
    }

    @Test
    void fixedSearchIsASingleTrialWithTheGivenValues() {
        Map<String, Object> hyperParams = new LinkedHashMap<>();
        hyperParams.put("epochs", 10);
        hyperParams.put("optional", null);

        List<Candidate> trials = trials(HyperParamSearchDTO.fixed(hyperParams));

        assertEquals(1, trials.size());
        assertEquals(hyperParams, trials.getFirst().getParams());
        assertEquals(1, trials(HyperParamSearchDTO.fixed(null)).size());
    }

    @Test
    void randomSearchIsRepeatable() throws Exception {
        HyperParamSearchDTO search = randomSearch(5);
        search.getSpace().put("lr", ParamDomainDTO.logUniform(1e-4, 1e-1));
        search.getSpace().put("dropout", ParamDomainDTO.uniform(0, 0.5));

        List<Candidate> first = trials(search);

        assertEquals(5, first.size());
        assertEquals(first, trials(search));
        first.forEach(trial -> {
            double lr = (double) trial.getParams().get("lr");
            assertTrue(lr >= 1e-4 && lr <= 1e-1, "lr out of range: " + lr);
        });
        ObjectMapper mapper = new ObjectMapper();
        HyperParamSearchDTO stored = mapper.readValue(mapper.writeValueAsString(search), HyperParamSearchDTO.class);
        assertEquals(first, trials(stored));
    }

    @Test
    void randomSearchOnAFiniteSpaceDrawsDistinctCombinationsAndCapsAtItsSize() {
        HyperParamSearchDTO search = randomSearch(50);
        search.getSpace().put("a", ParamDomainDTO.intRange(1, 4));
        search.getSpace().put("b", ParamDomainDTO.choice(List.of(true, false)));

        List<Candidate> trials = trials(search);

        assertEquals(8, trials.size());
        Set<Map<String, Object>> seen = new HashSet<>();
        trials.forEach(trial -> assertTrue(seen.add(trial.getParams())));
    }

    @Test
    void validationRejectsSearchesThatCannotRun() {
        HyperParamSearchDTO continuousGrid = new HyperParamSearchDTO();
        continuousGrid.getSpace().put("lr", ParamDomainDTO.logUniform(1e-4, 1e-1));
        assertInvalid(continuousGrid, "gridSearchable");
        assertThrows(IllegalArgumentException.class, () -> trials(continuousGrid));

        HyperParamSearchDTO hugeGrid = new HyperParamSearchDTO();
        hugeGrid.getSpace().put("a", ParamDomainDTO.intRange(0, 99));
        hugeGrid.getSpace().put("b", ParamDomainDTO.intRange(0, 99));
        assertInvalid(hugeGrid, "gridSearchable");

        HyperParamSearchDTO badDomain = randomSearch(1);
        badDomain.getSpace().put("lr", ParamDomainDTO.logUniform(0, 1));
        badDomain.getSpace().put("loss", ParamDomainDTO.choice(List.of()));
        assertInvalid(badDomain, "space[lr].valid", "space[loss].valid");

        assertInvalid(randomSearch(0), "iterations");
    }

    @Test
    void searchRoundTripsThroughJson() throws Exception {
        String json = """
                {"strategy":"RANDOM","iterations":4,"metric":"auc","direction":"MINIMIZE",
                 "space":{"lr":{"type":"LOG_UNIFORM","low":0.0001,"high":0.1},
                          "depth":{"type":"INT_RANGE","low":2,"high":6},
                          "loss":{"type":"CHOICE","values":["gini","entropy"]},
                          "epochs":{"type":"FIXED","value":10}}}
                """;
        ObjectMapper mapper = new ObjectMapper();

        HyperParamSearchDTO search = mapper.readValue(json, HyperParamSearchDTO.class);
        String written = mapper.writeValueAsString(search);
        HyperParamSearchDTO copy = mapper.readValue(written, HyperParamSearchDTO.class);

        assertEquals(ParamDomainDTO.intRange(2, 6), search.getSpace().get("depth"));
        assertEquals(search, copy);
        assertFalse(written.contains("valid") || written.contains("gridSearchable"), written);
        assertTrue(VALIDATOR.validate(search).isEmpty());
        assertEquals(4, trials(copy).size());
    }

    private static void assertInvalid(HyperParamSearchDTO search, String... paths) {
        Set<String> violated = new HashSet<>();
        for (ConstraintViolation<HyperParamSearchDTO> violation : VALIDATOR.validate(search)) {
            violated.add(violation.getPropertyPath().toString());
        }
        assertEquals(Set.of(paths), violated);
    }

    @Test
    void strategyFollowsTheConfiguredType() {
        assertInstanceOf(GridSearch.class, SearchStrategy.of(SearchStrategyType.GRID));
        assertInstanceOf(RandomSearch.class, SearchStrategy.of(SearchStrategyType.RANDOM));
    }

    private static List<Candidate> trials(HyperParamSearchDTO search) {
        return SearchStrategy.of(search.getStrategy()).trials(search);
    }

    private static HyperParamSearchDTO randomSearch(int iterations) {
        HyperParamSearchDTO search = new HyperParamSearchDTO();
        search.setStrategy(SearchStrategyType.RANDOM);
        search.setIterations(iterations);
        return search;
    }
}
