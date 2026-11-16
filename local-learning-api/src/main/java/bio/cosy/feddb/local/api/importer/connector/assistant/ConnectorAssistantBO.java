package bio.cosy.feddb.local.api.importer.connector.assistant;

import bio.cosy.feddb.local.agent.etl.transform.TransformerAgentStarter;
import bio.cosy.feddb.local.agent.etl.transform.assistant.TransformerAssistantMessageDTO;
import bio.cosy.feddb.local.agent.etl.transform.assistant.TransformerAssistantStream;
import bio.cosy.feddb.local.api.importer.connector.ConnectorDTO;
import io.quarkus.logging.Log;
import io.smallrye.mutiny.Multi;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static bio.cosy.feddb.local.agent.etl.transform.assistant.TransformerAssistantMessageType.APPLIED;


@ApplicationScoped
public class ConnectorAssistantBO {

    private static final String SESSION_PREFIX = "connector-";

    @Inject
    TransformerAgentStarter agentStarter;

    private final Map<String, Run> runs = new ConcurrentHashMap<>();

    public ConnectorAssistantRunDTO start(ConnectorDTO connector, String keycloakId) {
        String id = UUID.randomUUID().toString();
        Run run = new Run(keycloakId, new TransformerAssistantStream());
        runs.put(id, run);
        agentStarter.start(SESSION_PREFIX + id, connector, keycloakId, run.stream()::emit);
        return new ConnectorAssistantRunDTO(id);
    }

    public Multi<TransformerAssistantMessageDTO> events(String id, String keycloakId) {
        return getRun(id, keycloakId).stream().events()
                .onTermination().invoke(() -> close(id));
    }

    public void message(String id, TransformerAssistantMessageDTO message, String keycloakId) {
        Run run = getRun(id, keycloakId);
        String sessionId = SESSION_PREFIX + id;
        switch (message.getType()) {
            case USER_MESSAGE -> agentStarter.message(sessionId, message);
            case STOP -> {
                agentStarter.stop(sessionId);
                run.stream().stop();
            }
            case APPLY -> {
                TransformerAssistantMessageDTO applied = new TransformerAssistantMessageDTO(APPLIED);
                applied.setConnector(agentStarter.apply(sessionId, message.getSchemaIds()));
                run.stream().emit(applied);
            }
            default -> Log.debugf("Ignoring %s sent by the client", message.getType());
        }
    }

    private Run getRun(String id, String keycloakId) {
        return Optional.ofNullable(runs.get(id))
                .filter(run -> run.keycloakId().equals(keycloakId))
                .orElseThrow(() -> new NotFoundException("No transformer assistant run " + id));
    }

    private void close(String id) {
        Optional.ofNullable(runs.remove(id)).ifPresent(run -> {
            agentStarter.close(SESSION_PREFIX + id);
            run.stream().close();
        });
    }

    @PreDestroy
    void closeAll() {
        runs.keySet().forEach(this::close);
    }

    private record Run(String keycloakId, TransformerAssistantStream stream) {
    }
}
