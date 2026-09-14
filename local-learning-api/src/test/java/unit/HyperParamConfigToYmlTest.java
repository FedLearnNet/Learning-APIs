package unit;

import bio.cosy.feddb.local.api.workflow.node.HyperParamConfigToYml;
import com.fasterxml.jackson.core.JsonProcessingException;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
public class HyperParamConfigToYmlTest {

    @Test
    void testWriteYamlGeneratesExpectedStructure() throws JsonProcessingException {
        LinkedHashMap<String, Object> hyperParams = new LinkedHashMap<>();
        hyperParams.put("fc_random_forest.input.train", "data.csv");
        hyperParams.put("fc_random_forest.input.test", "data.csv");
        hyperParams.put("fc_random_forest.output.pred", "pred.csv");
        hyperParams.put("fc_random_forest.output.proba", "proba.csv");
        hyperParams.put("fc_random_forest.output.test", "test.csv");
        hyperParams.put("fc_random_forest.format.sep", ",");
        hyperParams.put("fc_random_forest.format.label", "10");
        hyperParams.put("fc_random_forest.split.mode", "file");
        hyperParams.put("fc_random_forest.split.dir", ".");
        hyperParams.put("fc_random_forest.estimators", 100);
        hyperParams.put("fc_random_forest.mode", "classification");
        hyperParams.put("fc_random_forest.random_state", 42);


        String expected = "fc_random_forest.input.train:data.csvfc_random_forest.input.test:data.csvfc_random_forest.output.pred:pred.csvfc_random_forest.output.proba:proba.csvfc_random_forest.output.test:test.csvfc_random_forest.format.sep:,fc_random_forest.format.label:10fc_random_forest.split.mode:filefc_random_forest.split.dir:.fc_random_forest.estimators:100fc_random_forest.mode:classificationfc_random_forest.random_state:42";

        String actual = HyperParamConfigToYml.createYaml(hyperParams);
        actual = actual.replaceAll("\\s+", "").replace("---", "").replace("\"", ""); // Remove all whitespace for comparison
        expected = expected.replaceAll("\\s+", "").replace("\"", ""); // Remove all whitespace for comparison
        // Assert the structures are equal it has leading ---
        assertEquals(actual, expected);
    }

}
