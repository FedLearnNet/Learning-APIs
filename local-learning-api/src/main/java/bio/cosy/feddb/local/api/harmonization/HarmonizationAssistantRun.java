package bio.cosy.feddb.local.api.harmonization;

import bio.cosy.feddb.local.agent.etl.transform.assistant.TransformerAssistantMessageDTO;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.subscription.MultiEmitter;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static bio.cosy.feddb.local.agent.etl.transform.assistant.TransformerAssistantMessageType.DONE;
import static bio.cosy.feddb.local.agent.etl.transform.assistant.TransformerAssistantMessageType.ERROR;

public class HarmonizationAssistantRun {

    private final List<TransformerAssistantMessageDTO> events = new CopyOnWriteArrayList<>();
    private final List<MultiEmitter<? super TransformerAssistantMessageDTO>> clients = new CopyOnWriteArrayList<>();
    private volatile boolean running = true;

    synchronized void emit(TransformerAssistantMessageDTO event) {
        if (event.getType() == DONE || event.getType() == ERROR) {
            running = false;
        }
        events.add(event);
        clients.forEach(client -> client.emit(event));
    }

    Multi<TransformerAssistantMessageDTO> events() {
        return Multi.createFrom().emitter(client -> {
            synchronized (this) {
                events.forEach(client::emit);
                clients.add(client);
            }
            client.onTermination(() -> clients.remove(client));
        });
    }

    boolean isRunning() {
        return running;
    }

    void stop() {
        running = false;
    }

    void close() {
        clients.forEach(MultiEmitter::complete);
    }
}
