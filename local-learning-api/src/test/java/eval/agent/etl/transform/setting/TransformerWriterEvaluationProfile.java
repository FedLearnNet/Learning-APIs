package eval.agent.etl.transform.setting;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.Map;

public class TransformerWriterEvaluationProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
                "quarkus.oidc.enabled", "false",
                "flnet.global.socket.enabled", "false",
                "quarkus.test.hang-detection-timeout", "60m");
    }
}
