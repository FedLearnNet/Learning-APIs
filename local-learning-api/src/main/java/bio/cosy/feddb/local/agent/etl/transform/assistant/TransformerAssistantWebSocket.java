package bio.cosy.feddb.local.agent.etl.transform.assistant;

import bio.cosy.feddb.core.api.model.workflow.chat.HumanInTheLoopDTO;
import bio.cosy.feddb.core.api.model.workflow.chat.ModelWorkflowChatMessageDTO;
import bio.cosy.feddb.local.agent.etl.transform.TransformerLoop;
import bio.cosy.feddb.local.api.importer.connector.ConnectorDTO;
import io.quarkus.logging.Log;
import io.quarkus.security.Authenticated;
import io.quarkus.websockets.next.OnClose;
import io.quarkus.websockets.next.OnOpen;
import io.quarkus.websockets.next.OnTextMessage;
import io.quarkus.websockets.next.OpenConnections;
import io.quarkus.websockets.next.WebSocket;
import io.quarkus.websockets.next.WebSocketConnection;
import jakarta.enterprise.context.SessionScoped;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import static bio.cosy.feddb.local.agent.etl.transform.assistant.TransformerAssistantMessageType.DONE;
import static bio.cosy.feddb.local.agent.etl.transform.assistant.TransformerAssistantMessageType.ERROR;

@SessionScoped
@Authenticated
@WebSocket(path = "/connectors/transformer-assistant")
public class TransformerAssistantWebSocket {

    @Inject
    TransformerLoop transformerLoop;

    @Inject
    OpenConnections openConnections;

    private final AtomicLong messageIds = new AtomicLong();
    private final List<String> instructions = new CopyOnWriteArrayList<>();
    private volatile CompletableFuture<String> pendingAnswer;
    private volatile AtomicBoolean cancelled = new AtomicBoolean();
    private volatile Thread run;
    private String connectionId;

    @OnOpen
    public void onOpen(WebSocketConnection connection) {
        connectionId = connection.id();
    }

    @OnTextMessage
    public void onMessage(TransformerAssistantMessageDTO message) {
        switch (message.getType()) {
            case START -> start(message.getConnector());
            case USER_MESSAGE -> answerOrInstruct(message.getMessage());
            case STOP -> stop();
            default -> Log.debugf("Ignoring %s sent by the client", message.getType());
        }
    }

    @OnClose
    public void onClose() {
        stop();
    }

    private void start(ConnectorDTO connector) {
        stop();
        instructions.clear();
        AtomicBoolean runCancelled = new AtomicBoolean();
        cancelled = runCancelled;
        Consumer<TransformerAssistantMessageDTO> events = message -> {
            if (runCancelled.get()) {
                throw new CancellationException();
            }
            send(message);
        };
        run = Thread.ofVirtual().start(() -> {
            try {
                transformerLoop.selectAfterMerge(connector, events, () -> String.join("\n", instructions),
                        question -> askUser(question, events));
                events.accept(new TransformerAssistantMessageDTO(DONE));
            } catch (CancellationException e) {
                Log.debug("Transformer assistant stopped");
            } catch (RuntimeException e) {
                Log.warn("Transformer assistant failed", e);
                TransformerAssistantMessageDTO error = new TransformerAssistantMessageDTO(ERROR);
                error.setMessage(e.getMessage());
                send(error);
            }
        });
    }

    private String askUser(String question, Consumer<TransformerAssistantMessageDTO> events) {
        ModelWorkflowChatMessageDTO chat = new ModelWorkflowChatMessageDTO();
        chat.setId(messageIds.incrementAndGet());
        chat.setCreatedAt(new Date());
        chat.setMessage(question);
        chat.setStatusMessage("Waiting for your answer");
        chat.setHumanInTheLoop(new ArrayList<>(List.of(new HumanInTheLoopDTO(question))));
        CompletableFuture<String> answer = new CompletableFuture<>();
        pendingAnswer = answer;
        events.accept(TransformerAssistantMessageDTO.chat(chat));
        try {
            String text = answer.get();
            chat.addHumanInTheLoopAnswer(text);
            chat.setMessage(question + "\n\n> " + text);
            chat.setStatusMessage(null);
            chat.setDone(true);
            events.accept(TransformerAssistantMessageDTO.chat(chat));
            return text;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CancellationException();
        } catch (ExecutionException e) {
            throw new CancellationException();
        } finally {
            pendingAnswer = null;
        }
    }

    private void answerOrInstruct(String text) {
        CompletableFuture<String> pending = pendingAnswer;
        if (pending != null) {
            pending.complete(text);
        } else {
            instructions.add(text);
        }
    }

    private void stop() {
        cancelled.set(true);
        CompletableFuture<String> pending = pendingAnswer;
        if (pending != null) {
            pending.cancel(true);
        }
        Thread current = run;
        if (current != null) {
            current.interrupt();
        }
        run = null;
    }

    private void send(TransformerAssistantMessageDTO message) {
        openConnections.findByConnectionId(connectionId).ifPresent(connection -> connection.sendTextAndAwait(message));
    }
}
