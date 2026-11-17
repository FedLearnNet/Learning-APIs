package eval.agent.etl.transform.pipeline;

import bio.cosy.feddb.core.api.model.workflow.chat.ModelWorkflowChatMessageDTO;
import bio.cosy.feddb.local.agent.etl.transform.assistant.TransformerAssistantMessageDTO;
import bio.cosy.feddb.local.agent.etl.transform.model.ConnectorSelectedTransformer;
import bio.cosy.feddb.local.api.importer.transformer.ConnectorTransformerDTO;
import eval.agent.etl.transform.pojo.TransformerWriterEffort;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * The effort per schema node, read from the messages of the assistant: the time from its start to its result, the
 * model calls in between, the questions to the user and the validations in its steps. A node of the review pass adds
 * to its first pass.
 */
public class EffortTracker {

    // TransformerValidateTool logs every validation as '<chain> valid' or '<chain> rejected (<reason>)'
    private static final Pattern VALIDATION = Pattern.compile(" (valid|rejected \\(.*\\))$", Pattern.DOTALL);
    private static final String BUILT = "custom code '";

    private final ModelCallCounter calls;
    private final Map<Long, TransformerWriterEffort> efforts = new LinkedHashMap<>();
    private Long current;
    private long started;
    private int callsAtStart;

    public EffortTracker(ModelCallCounter calls) {
        this.calls = calls;
    }

    public synchronized void accept(TransformerAssistantMessageDTO message) {
        switch (message.getType()) {
            case NODE_STARTED -> {
                current = message.getSchemaId();
                started = System.currentTimeMillis();
                callsAtStart = calls.calls();
                efforts.computeIfAbsent(current, id -> new TransformerWriterEffort(message.getMappingPath()));
            }
            case CHAT_MESSAGE -> {
                if (current != null && isQuestion(message.getChatMessage())) {
                    efforts.get(current).setQuestions(efforts.get(current).getQuestions() + 1);
                }
            }
            case NODE_RESULT -> finish(message);
            default -> {
            }
        }
    }

    public synchronized List<TransformerWriterEffort> efforts() {
        return List.copyOf(efforts.values());
    }

    private void finish(TransformerAssistantMessageDTO message) {
        TransformerWriterEffort effort = efforts.computeIfAbsent(message.getSchemaId(),
                id -> new TransformerWriterEffort(message.getMappingPath()));
        if (message.getSchemaId().equals(current)) {
            effort.setDurationMs(effort.getDurationMs() + System.currentTimeMillis() - started);
            effort.setModelCalls(effort.getModelCalls() + calls.calls() - callsAtStart);
            current = null;
        }
        List<String> steps = message.getSteps() == null ? List.of() : message.getSteps();
        effort.setValidations(effort.getValidations() + (int) steps.stream().filter(step -> VALIDATION.matcher(step).find()).count());
        List<String> all = new ArrayList<>(effort.getSteps());
        all.addAll(steps);
        effort.setSteps(all);
        effort.setSource(source(message.getSelectedTransformer(), steps));
    }

    private static boolean isQuestion(ModelWorkflowChatMessageDTO chat) {
        return chat != null && !chat.isDone() && chat.getHumanInTheLoop() != null && !chat.getHumanInTheLoop().isEmpty();
    }

    private static String source(ConnectorSelectedTransformer selected, List<String> steps) {
        if (selected == null || !selected.isFoundTransformer()) {
            return "not found";
        }
        List<ConnectorTransformerDTO> chain = selected.getSelectedTransformers();
        if (chain.isEmpty()) {
            return "direct";
        }
        if (chain.stream().anyMatch(ConnectorTransformerDTO::isCodeTransformer)) {
            // TransformerWriterTool logs "custom code '<name>' built" when it saves code for this node
            return steps.stream().anyMatch(step -> step.startsWith(BUILT) && step.contains("' built")) ? "new code" : "saved code";
        }
        return chain.stream().anyMatch(ConnectorTransformerDTO::isStoreTool) ? "store tool" : "built-in";
    }
}
