package bio.cosy.feddb.local.api.importer.run.message;

import bio.cosy.feddb.core.api.run.message.RunMessageTypes;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class ConnectorRunMessagesAO implements PanacheRepository<ConnectorRunMessagesEntity> {

    public List<ConnectorRunMessagesEntity> findByRun(Long id) {
        List<ConnectorRunMessagesEntity> runs = list("run.id = ?1 and type = ?2",
                Sort.by("createdAt", Sort.Direction.Ascending),
                id, RunMessageTypes.LOG);
        runs.addAll(list("step.connectorRun.id = ?1 and type = ?2",
                Sort.by("createdAt", Sort.Direction.Ascending),
                id, RunMessageTypes.LOG));
        return runs;
    }

    public Optional<ConnectorRunMessagesEntity> findLastLogByStep(Long id) {
        return find("step.id = ?1 and type = ?2 order by id desc",
                id, RunMessageTypes.LOG)
                .firstResultOptional();
    }

    public int detachTransformer(Long transformerId) {
        return update("transformer = null where transformer.id = ?1", transformerId);
    }

    public Optional<ConnectorRunMessagesEntity> findLastLogByPrediction(Long id) {
        return find("step.id = ?1 and type = ?2 order by id desc",
                id, RunMessageTypes.LOG)
                .firstResultOptional();
    }

}
