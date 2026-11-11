package bio.cosy.feddb.local.api.importer.run.message;

import bio.cosy.feddb.core.api.run.message.RunMessageTypes;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class ConnectorRunMessagesAO implements PanacheRepository<ConnectorRunMessagesEntity> {

    /**
     * Loads all log messages of a run in one query: messages attached to the run directly and
     * messages attached to one of its steps. A message linked both ways is returned once.
     */
    public List<ConnectorRunMessagesEntity> findByRun(Long id) {
        return find("""
                        select m from ConnectorRunMessagesEntity m
                        left join m.step s
                        where (m.run.id = ?1 or s.connectorRun.id = ?1) and m.type = ?2
                        order by m.createdAt asc, m.id asc
                        """,
                id, RunMessageTypes.LOG).list();
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
