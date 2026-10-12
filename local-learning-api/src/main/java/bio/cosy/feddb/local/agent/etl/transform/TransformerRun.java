package bio.cosy.feddb.local.agent.etl.transform;

import bio.cosy.feddb.core.api.model.workflow.chat.HumanInTheLoopDTO;
import bio.cosy.feddb.core.api.model.workflow.chat.HumanInTheLoopType;
import bio.cosy.feddb.core.api.model.workflow.chat.ModelWorkflowChatMessageDTO;
import bio.cosy.feddb.local.agent.etl.transform.assistant.TransformerAssistantMessageDTO;
import bio.cosy.feddb.local.agent.etl.transform.assistant.TransformerAssistantUser;
import bio.cosy.feddb.local.agent.etl.transform.helper.EtlAgentLog;
import io.quarkus.logging.Log;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static bio.cosy.feddb.local.agent.etl.transform.assistant.TransformerAssistantMessageType.*;

/**
 * One transformer assistant run of a WebSocket session: streams events to the client, collects the node results
 * and routes user messages either as answer to a pending question or as additional instruction.
 */
final class TransformerRun implements TransformerAssistantUser {

    private final Consumer<TransformerAssistantMessageDTO> send;
    private final AtomicLong messageIds = new AtomicLong();
    private final List<String> instructions = new CopyOnWriteArrayList<>();
    private final Map<Long, TransformerAssistantMessageDTO> results = new ConcurrentHashMap<>();
    private volatile CompletableFuture<TransformerAssistantMessageDTO> pendingAnswer = CompletableFuture.completedFuture(null);
    private volatile TransformerAssistantMessageDTO patientId;
    private volatile boolean cancelled;
    private volatile Future<?> task;

    TransformerRun(Consumer<TransformerAssistantMessageDTO> send) {
        this.send = send;
    }

    void start(ExecutorService executor, Runnable work) {
        task = executor.submit(() -> {
            try {
                work.run();
                emit(new TransformerAssistantMessageDTO(DONE));
            } catch (CancellationException e) {
                Log.debug("Transformer assistant stopped");
            } catch (RuntimeException e) {
                Log.warn("Transformer assistant failed", e);
                TransformerAssistantMessageDTO error = new TransformerAssistantMessageDTO(ERROR);
                error.setMessage(e.getMessage());
                send.accept(error);
            }
        });
    }

    void emit(TransformerAssistantMessageDTO message) {
        if (cancelled) {
            throw new CancellationException();
        }
        if (message.getType() == NODE_RESULT) {
            results.put(message.getSchemaId(), message);
        }
        if (message.getType() == PATIENT_ID) {
            patientId = message;
        }
        send.accept(message);
    }

    String instructions() {
        return String.join("\n", instructions);
    }

    @Override
    public String ask(String agent, String question) {
        return awaitAnswer(agent, new HumanInTheLoopDTO(question)).getMessage();
    }

    @Override
    public List<String> selectColumns(String agent, String question, List<String> columns, boolean multiple) {
        HumanInTheLoopType type = multiple ? HumanInTheLoopType.SELECT_COLUMNS : HumanInTheLoopType.SELECT_COLUMN;
        TransformerAssistantMessageDTO answer = awaitAnswer(agent, new HumanInTheLoopDTO(question, type, columns));
        List<String> selected = answer.getColumns() == null || answer.getColumns().isEmpty()
                ? Stream.ofNullable(answer.getMessage()).map(String::strip).toList()
                : answer.getColumns();
        return selected.stream()
                .filter(columns::contains)
                .distinct()
                .limit(multiple ? columns.size() : 1)
                .toList();
    }

    private TransformerAssistantMessageDTO awaitAnswer(String agent, HumanInTheLoopDTO question) {
        ModelWorkflowChatMessageDTO chat = new ModelWorkflowChatMessageDTO();
        chat.setId(messageIds.incrementAndGet());
        chat.setCreatedAt(new Date());
        chat.setMessage(question.getQuestion());
        chat.setStatusMessage("Waiting for your answer");
        chat.setHumanInTheLoop(new ArrayList<>(List.of(question)));
        CompletableFuture<TransformerAssistantMessageDTO> answer = new CompletableFuture<>();
        pendingAnswer = answer;
        EtlAgentLog.step(agent, "Asks the user: %s", EtlAgentLog.shorten(question.getQuestion()));
        emit(TransformerAssistantMessageDTO.chat(chat, agent));
        // join() throws CancellationException once cancel() cancels the pending answer
        TransformerAssistantMessageDTO reply = answer.join();
        String text = reply.getColumns() == null || reply.getColumns().isEmpty()
                ? reply.getMessage()
                : String.join(", ", reply.getColumns());
        EtlAgentLog.step(agent, "The user answers: %s", EtlAgentLog.shorten(text));
        chat.addHumanInTheLoopAnswer(text);
        chat.setMessage(question.getQuestion() + "\n\n> " + text);
        chat.setStatusMessage(null);
        chat.setDone(true);
        emit(TransformerAssistantMessageDTO.chat(chat, agent));
        return reply;
    }

    void answerOrInstruct(TransformerAssistantMessageDTO reply) {
        if (!pendingAnswer.complete(reply) && reply.getMessage() != null && !reply.getMessage().isBlank()) {
            instructions.add(reply.getMessage());
        }
    }

    /** The results of the given schema nodes, plus the patient id column the user selected during the run. */
    List<TransformerAssistantMessageDTO> results(List<Long> schemaIds) {
        Stream<TransformerAssistantMessageDTO> nodes = schemaIds.stream().map(results::get).filter(Objects::nonNull);
        return Stream.concat(nodes, Stream.ofNullable(patientId)).toList();
    }

    void cancel() {
        cancelled = true;
        pendingAnswer.cancel(true);
        Future<?> current = task;
        if (current != null) {
            current.cancel(true);
        }
    }
}
