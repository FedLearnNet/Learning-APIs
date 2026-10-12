package bio.cosy.feddb.local.agent.etl.transform.guardrails;

import bio.cosy.feddb.local.agent.etl.transform.model.ConnectorSelectedTransformer;
import dev.langchain4j.guardrail.OutputGuardrailResult;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Map;

@ApplicationScoped
public class TransformerSelectGuardrail extends TransformerAnswerGuardrail {

    @Override
    protected OutputGuardrailResult check(ConnectorSelectedTransformer answer, Map<String, Object> variables) {
        return answer.isFoundTransformer() ? checkFunctions(answer) : checkNotFound(answer, variables);
    }
}
