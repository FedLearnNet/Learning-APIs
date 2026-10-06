package bio.cosy.feddb.local.agent.etl.transform;

import bio.cosy.feddb.local.agent.etl.transform.assistant.TransformerAssistantMessageDTO;
import bio.cosy.feddb.local.agent.etl.transform.helper.TransformerCombiner;
import bio.cosy.feddb.local.agent.etl.transform.helper.TransformerHelper;
import bio.cosy.feddb.local.agent.etl.transform.organizer.TransformerLoop;
import bio.cosy.feddb.local.api.importer.connector.ConnectorDTO;
import io.quarkus.virtual.threads.VirtualThreads;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

@ApplicationScoped
public class TransformerAgentStarter {

    @Inject
    TransformerLoop transformerLoop;

    @Inject
    @VirtualThreads
    ExecutorService executor;

    @Inject
    TransformerCombiner combiner;

    private final Map<String, TransformerRun> runs = new ConcurrentHashMap<>();

    public void start(String sessionId, ConnectorDTO connector, String keycloakId, Consumer<TransformerAssistantMessageDTO> send) {
        TransformerRun run = new TransformerRun(send);
        Optional.ofNullable(runs.put(sessionId, run)).ifPresent(TransformerRun::cancel);
        run.start(executor, () -> transformerLoop.run(connector, keycloakId, run::emit, run::instructions, run));
    }

    public void message(String sessionId, TransformerAssistantMessageDTO message) {
        Optional.ofNullable(runs.get(sessionId)).ifPresent(run -> run.answerOrInstruct(message));
    }

    public ConnectorDTO apply(String sessionId, List<Long> schemaIds) {
        TransformerRun run = runs.get(sessionId);
        if (run == null || schemaIds == null || schemaIds.isEmpty()) {
            return TransformerHelper.toConnector(List.of());
        }
        ConnectorDTO connector = TransformerHelper.toConnector(run.results(schemaIds));
        connector.setTransformer(combiner.combine(connector.getTransformer()));
        return connector;
    }

    public void stop(String sessionId) {
        Optional.ofNullable(runs.get(sessionId)).ifPresent(TransformerRun::cancel);
    }

    public void close(String sessionId) {
        Optional.ofNullable(runs.remove(sessionId)).ifPresent(TransformerRun::cancel);
    }

    @PreDestroy
    void cancelAll() {
        runs.values().forEach(TransformerRun::cancel);
    }
}
