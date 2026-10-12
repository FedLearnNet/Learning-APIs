package eval.agent.etl.transform.pipeline;

import bio.cosy.feddb.core.api.model.workflow.chat.HumanInTheLoopDTO;
import bio.cosy.feddb.core.api.model.workflow.chat.HumanInTheLoopType;
import bio.cosy.feddb.core.api.model.workflow.chat.ModelWorkflowChatMessageDTO;
import bio.cosy.feddb.local.agent.etl.transform.TransformerAgentStarter;
import bio.cosy.feddb.local.agent.etl.transform.assistant.TransformerAssistantMessageDTO;
import bio.cosy.feddb.local.api.importer.connector.ConnectorDTO;
import bio.cosy.feddb.local.config.FLNetClientConfig;
import eval.agent.etl.transform.pojo.TransformerWriterSuggestion;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import static bio.cosy.feddb.local.agent.etl.transform.assistant.TransformerAssistantMessageType.USER_MESSAGE;

@ApplicationScoped
public class TransformerWriterAssistant {

    private static final String NO_ANSWER = "I don't know, please decide from the data.";
    private static final long TIMEOUT_HOURS = 6;

    @Inject
    TransformerAgentStarter starter;

    @Inject
    FLNetClientConfig config;

    public TransformerWriterSuggestion suggest(ConnectorDTO connector, String patientIdColumn) {
        return suggest(patientIdColumn, (session, send) ->
                starter.start(session, connector, config.user().systemUserName(), send));
    }

    public TransformerWriterSuggestion suggest(String patientIdColumn,
                                               BiConsumer<String, Consumer<TransformerAssistantMessageDTO>> start) {
        String session = UUID.randomUUID().toString();
        List<TransformerAssistantMessageDTO> nodes = new CopyOnWriteArrayList<>();
        CompletableFuture<Void> done = new CompletableFuture<>();
        start.accept(session, message -> handle(session, message, patientIdColumn, nodes, done));
        try {
            done.orTimeout(TIMEOUT_HOURS, TimeUnit.HOURS).join();
            return new TransformerWriterSuggestion(List.copyOf(nodes), starter.apply(session, foundSchemaIds(nodes)));
        } finally {
            starter.close(session);
        }
    }

    public static boolean isFound(TransformerAssistantMessageDTO node) {
        return node.getSelectedTransformer() != null && node.getSelectedTransformer().isFoundTransformer();
    }

    private void handle(String session, TransformerAssistantMessageDTO message, String patientIdColumn,
                        List<TransformerAssistantMessageDTO> nodes, CompletableFuture<Void> done) {
        switch (message.getType()) {
            case CHAT_MESSAGE -> answer(session, message, patientIdColumn);
            case NODE_RESULT -> addNode(nodes, message);
            case DONE -> done.complete(null);
            case ERROR -> done.completeExceptionally(new IllegalStateException(message.getMessage()));
            default -> {
            }
        }
    }

    private static void addNode(List<TransformerAssistantMessageDTO> nodes, TransformerAssistantMessageDTO node) {
        nodes.add(node);
    }

    private void answer(String session, TransformerAssistantMessageDTO message, String patientIdColumn) {
        ModelWorkflowChatMessageDTO chat = message.getChatMessage();
        if (chat.isDone() || chat.getHumanInTheLoop() == null || chat.getHumanInTheLoop().isEmpty()) {
            return;
        }
        HumanInTheLoopDTO question = chat.getHumanInTheLoop().getFirst();
        starter.message(session, createAnswer(question, patientIdColumn));
    }

    /** The answer of the evaluation: the patient id column when asked for it, otherwise the agent decides. */
    public static TransformerAssistantMessageDTO createAnswer(HumanInTheLoopDTO question, String patientIdColumn) {
        TransformerAssistantMessageDTO answer = new TransformerAssistantMessageDTO(USER_MESSAGE);
        if (isPatientIdQuestion(question)) {
            answer.setColumns(List.of(patientIdColumn));
        } else {
            answer.setMessage(NO_ANSWER);
        }
        return answer;
    }

    private static boolean isPatientIdQuestion(HumanInTheLoopDTO question) {
        return question.getType() == HumanInTheLoopType.SELECT_COLUMN && question.getQuestion().contains("patient ID");
    }

    private static List<Long> foundSchemaIds(List<TransformerAssistantMessageDTO> nodes) {
        return nodes.stream()
                .filter(TransformerWriterAssistant::isFound)
                .map(TransformerAssistantMessageDTO::getSchemaId)
                .toList();
    }
}
