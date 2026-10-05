package bio.cosy.feddb.local.agent.etl.transform;

import bio.cosy.feddb.core.api.model.workflow.chat.HumanInTheLoopDTO;
import bio.cosy.feddb.core.api.model.workflow.chat.ModelWorkflowChatMessageDTO;
import bio.cosy.feddb.local.agent.etl.transform.assistant.TransformerAssistantMessageDTO;
import io.quarkus.logging.Log;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import static bio.cosy.feddb.local.agent.etl.transform.assistant.TransformerAssistantMessageType.DONE;
import static bio.cosy.feddb.local.agent.etl.transform.assistant.TransformerAssistantMessageType.ERROR;
import static bio.cosy.feddb.local.agent.etl.transform.assistant.TransformerAssistantMessageType.NODE_RESULT;

/**
 * One transformer assistant run of a WebSocket session: streams events to the client, collects the node results
 * and routes user messages either as answer to a pending question or as additional instruction.
 */
final class TransformerRun {

    private final Consumer<TransformerAssistantMessageDTO> send;
    private final AtomicLong messageIds = new AtomicLong();
    private final List<String> instructions = new CopyOnWriteArrayList<>();
    private final Map<Long, TransformerAssistantMessageDTO> results = new ConcurrentHashMap<>();
    private volatile CompletableFuture<String> pendingAnswer = CompletableFuture.completedFuture(null);
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
        send.accept(message);
    }

    String instructions() {
        return String.join("\n", instructions);
    }

    String askUser(String question) {
        ModelWorkflowChatMessageDTO chat = new ModelWorkflowChatMessageDTO();
        chat.setId(messageIds.incrementAndGet());
        chat.setCreatedAt(new Date());
        chat.setMessage(question);
        chat.setStatusMessage("Waiting for your answer");
        chat.setHumanInTheLoop(new ArrayList<>(List.of(new HumanInTheLoopDTO(question))));
        CompletableFuture<String> answer = new CompletableFuture<>();
        pendingAnswer = answer;
        emit(TransformerAssistantMessageDTO.chat(chat));
        // join() throws CancellationException once cancel() cancels the pending answer
        String text = answer.join();
        chat.addHumanInTheLoopAnswer(text);
        chat.setMessage(question + "\n\n> " + text);
        chat.setStatusMessage(null);
        chat.setDone(true);
        emit(TransformerAssistantMessageDTO.chat(chat));
        return text;
    }

    void answerOrInstruct(String text) {
        if (!pendingAnswer.complete(text)) {
            instructions.add(text);
        }
    }

    List<TransformerAssistantMessageDTO> results(List<Long> schemaIds) {
        return schemaIds.stream().map(results::get).filter(Objects::nonNull).toList();
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
