package bio.cosy.feddb.local.api.harmonization;

import bio.cosy.feddb.core.api.file.analytics.ColumnProfile;
import bio.cosy.feddb.local.agent.etl.transform.TransformerAgentStarter;
import bio.cosy.feddb.local.agent.etl.transform.assistant.TransformerAssistantMessageDTO;
import io.quarkus.logging.Log;
import io.smallrye.mutiny.Multi;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static bio.cosy.feddb.local.agent.etl.transform.assistant.TransformerAssistantMessageType.APPLIED;

@ApplicationScoped
public class HarmonizationAssistantBO {

    private static final String SESSION_PREFIX = "harmonization-";

    @Inject
    TransformerAgentStarter agentStarter;

    @Inject
    HarmonizationRunBO runBO;

    private final Map<Long, HarmonizationAssistantRun> runs = new ConcurrentHashMap<>();

    public synchronized void start(HarmonizationDTO harmonization, Map<String, ColumnProfile> fileProfiles, String keycloakId) {
        if (isRunning(harmonization.getId())) {
            return;
        }
        HarmonizationAssistantRun run = new HarmonizationAssistantRun();
        Optional.ofNullable(runs.put(harmonization.getId(), run)).ifPresent(HarmonizationAssistantRun::close);
        agentStarter.start(SESSION_PREFIX + harmonization.getId(), runBO.connector(harmonization), runBO.schemaNodes(harmonization),
                fileProfiles, keycloakId, run::emit);
    }

    public boolean isRunning(Long id) {
        HarmonizationAssistantRun run = runs.get(id);
        return run != null && run.isRunning();
    }

    public Multi<TransformerAssistantMessageDTO> events(Long id) {
        return Optional.ofNullable(runs.get(id)).map(HarmonizationAssistantRun::events)
                .orElseThrow(() -> new NotFoundException("No assistant run of harmonization " + id));
    }

    public void message(Long id, TransformerAssistantMessageDTO message) {
        String sessionId = SESSION_PREFIX + id;
        Optional<HarmonizationAssistantRun> run = Optional.ofNullable(runs.get(id));
        switch (message.getType()) {
            case USER_MESSAGE -> agentStarter.message(sessionId, message);
            case STOP -> {
                agentStarter.stop(sessionId);
                run.ifPresent(HarmonizationAssistantRun::stop);
            }
            case APPLY -> {
                TransformerAssistantMessageDTO applied = new TransformerAssistantMessageDTO(APPLIED);
                applied.setConnector(agentStarter.apply(sessionId, message.getSchemaIds()));
                run.ifPresent(current -> current.emit(applied));
            }
            default -> Log.debugf("Ignoring %s sent by the client", message.getType());
        }
    }

    @PreDestroy
    void closeAll() {
        runs.values().forEach(HarmonizationAssistantRun::close);
    }
}
