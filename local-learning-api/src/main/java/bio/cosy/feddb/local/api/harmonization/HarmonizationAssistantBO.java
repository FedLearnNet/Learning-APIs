package bio.cosy.feddb.local.api.harmonization;

import bio.cosy.feddb.core.api.file.analytics.ColumnProfile;
import bio.cosy.feddb.local.agent.etl.transform.TransformerAgentStarter;
import bio.cosy.feddb.local.agent.etl.transform.assistant.TransformerAssistantMessageDTO;
import bio.cosy.feddb.local.api.importer.mapping.ConnectorMappingDTO;
import bio.cosy.feddb.local.api.schema.schemanode.LocalSchemaNodeWithPathDTO;
import bio.cosy.feddb.local.config.FLNetClientConfig;
import io.quarkus.logging.Log;
import io.smallrye.mutiny.Multi;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static bio.cosy.feddb.local.agent.etl.transform.assistant.TransformerAssistantMessageType.APPLIED;

@ApplicationScoped
public class HarmonizationAssistantBO {

    private static final String SESSION_PREFIX = "harmonization-";

    @Inject
    TransformerAgentStarter agentStarter;

    @Inject
    HarmonizationConnectorMapper connectorMapper;

    @Inject
    FLNetClientConfig config;

    private final Map<Long, HarmonizationAssistantRun> runs = new ConcurrentHashMap<>();

    public synchronized void start(HarmonizationDTO harmonization, Map<String, ColumnProfile> fileProfiles, String keycloakId) {
        if (isRunning(harmonization.getId())) {
            return;
        }
        HarmonizationAssistantRun run = new HarmonizationAssistantRun();
        Optional.ofNullable(runs.put(harmonization.getId(), run)).ifPresent(HarmonizationAssistantRun::close);
        agentStarter.start(SESSION_PREFIX + harmonization.getId(),
                connectorMapper.toConnector(harmonization, config.connector().externalIdColumn()),
                unmappedTargets(harmonization), fileProfiles, keycloakId, run::emit);
    }

    private List<LocalSchemaNodeWithPathDTO> unmappedTargets(HarmonizationDTO harmonization) {
        Set<String> mapped = harmonization.getMapping().stream()
                .map(ConnectorMappingDTO::getMapping)
                .collect(Collectors.toSet());
        List<HarmonizationTargetDTO> targets = harmonization.getTargets();
        return IntStream.range(0, targets.size())
                .filter(index -> !mapped.contains(targets.get(index).getName()))
                .mapToObj(index -> connectorMapper.toSchemaNode(targets.get(index), index))
                .toList();
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
