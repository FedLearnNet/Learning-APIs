package eval.agent.etl.transform.setting;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.HashMap;
import java.util.Map;

public class TransformerWriterEvaluationProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        Map<String, String> overrides = new HashMap<>(Map.of(
                "quarkus.oidc.enabled", "false",
                "flnet.global.socket.enabled", "false",
                "quarkus.test.hang-detection-timeout", "60m"));
        // The configuration of the setting ends the mode, e.g. real-no-review (see run-us130-eval.sh)
        String mode = System.getProperty("eval.mode", "");
        if (mode.endsWith("-matcher-only")) {
            overrides.put("flnet.connector.agent.matcher-only", "true");
        } else if (mode.endsWith("-no-review")) {
            overrides.put("flnet.connector.agent.review", "false");
            overrides.put("flnet.connector.agent.review-agents", "false");
        } else if (mode.endsWith("-no-writer")) {
            overrides.put("flnet.connector.agent.writer", "false");
        }
        return overrides;
    }
}
