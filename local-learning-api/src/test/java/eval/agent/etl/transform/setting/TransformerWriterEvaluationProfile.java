package eval.agent.etl.transform.setting;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.Map;

public class TransformerWriterEvaluationProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
                "quarkus.oidc.enabled", "false",
                "flnet.global.socket.enabled", "false",
                "quarkus.test.hang-detection-timeout", "60m",
                "quarkus.langchain4j.judge.chat-model.provider", "openai",
                "quarkus.langchain4j.openai.judge.base-url", "${quarkus.langchain4j.openai.base-url}",
                "quarkus.langchain4j.openai.judge.api-key", "${quarkus.langchain4j.openai.api-key}",
                "quarkus.langchain4j.openai.judge.timeout", "${quarkus.langchain4j.openai.timeout}",
                "quarkus.langchain4j.openai.judge.chat-model.model-name", "${eval.judge-model:gpt-oss:latest}");
    }
}
