package bio.cosy.feddb.local.agent.etl.transform.guardrails;

import bio.cosy.feddb.local.agent.etl.transform.model.ConnectorSelectedTransformer;
import bio.cosy.feddb.local.agent.etl.transform.model.SavedCodeTransformer;
import bio.cosy.feddb.local.agent.etl.transform.tools.TransformerWriterTool;
import dev.langchain4j.guardrail.OutputGuardrailResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;
import java.util.Map;

@ApplicationScoped
public class TransformerWriterGuardrail extends TransformerAnswerGuardrail {

    @Inject
    TransformerWriterTool writerTool;

    @Override
    protected OutputGuardrailResult check(ConnectorSelectedTransformer answer, Map<String, Object> variables) {
        SavedCodeTransformer saved = writerTool.savedAnswer();
        if (answer.isFoundTransformer() && saved != null) {
            return rewritten(ConnectorSelectedTransformer.mapping(saved.getMappedColumn(), List.of(saved.getTransformer())));
        }
        boolean unknown = answer.isFoundTransformer()
                && !unknownTransformers(answer.getSelectedTransformers()).isEmpty();
        if ((unknown || !answer.isFoundTransformer()) && writerTool.unavailable() != null) {
            return rewritten(ConnectorSelectedTransformer.notFound("Custom code cannot be used: " + writerTool.unavailable()));
        }
        if (unknown && hints.once("unsaved code")) {
            return feedback("Your answer names a transformer that does not exist. Write the code, run checkCode until it "
                    + "succeeds, save it with saveCode and answer with the transformer saveCode returns; or set "
                    + "foundTransformer to false and explain why no code can compute the schema node.");
        }
        return answer.isFoundTransformer() ? checkFunctions(answer) : checkNotFound(answer, variables);
    }
}
