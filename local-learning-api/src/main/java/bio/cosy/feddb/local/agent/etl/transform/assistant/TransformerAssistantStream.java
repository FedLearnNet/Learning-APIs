package bio.cosy.feddb.local.agent.etl.transform.assistant;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.subscription.MultiEmitter;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static bio.cosy.feddb.local.agent.etl.transform.assistant.TransformerAssistantMessageType.DONE;
import static bio.cosy.feddb.local.agent.etl.transform.assistant.TransformerAssistantMessageType.ERROR;

/**
 * Events of an assistant run in the background: a client that subscribes late gets the events so far first.
 * Independent of the access token of the client, so a run outlasts it.
 */
public class TransformerAssistantStream {

    private final List<TransformerAssistantMessageDTO> events = new CopyOnWriteArrayList<>();
    private final List<MultiEmitter<? super TransformerAssistantMessageDTO>> clients = new CopyOnWriteArrayList<>();
    private volatile boolean running = true;

    public synchronized void emit(TransformerAssistantMessageDTO event) {
        if (event.getType() == DONE || event.getType() == ERROR) {
            running = false;
        }
        events.add(event);
        clients.forEach(client -> client.emit(event));
    }

    public Multi<TransformerAssistantMessageDTO> events() {
        return Multi.createFrom().emitter(client -> {
            synchronized (this) {
                events.forEach(client::emit);
                clients.add(client);
            }
            client.onTermination(() -> clients.remove(client));
        });
    }

    public boolean isRunning() {
        return running;
    }

    public void stop() {
        running = false;
    }

    public void close() {
        clients.forEach(MultiEmitter::complete);
    }
}
