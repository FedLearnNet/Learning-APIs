package bio.cosy.feddb.local.api.importer.run.step;

import bio.cosy.feddb.core.api.run.RunStatusTypes;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.util.Date;
import java.util.Optional;

@ApplicationScoped
public class ConnectorRunStepAO implements PanacheRepository<ConnectorRunStepEntity> {

    public Optional<ConnectorRunStepEntity> getByContainerId(String containerId, String keycloakId) {
        return find("containerId = ?1 and experiment.keycloakId = ?2", containerId, keycloakId).firstResultOptional();
    }

    @Transactional
    public boolean updateStatusTransactional(Long runId, RunStatusTypes status) {
        int updated = update(
                """
                        status = ?1,
                        updatedAt = ?2
                        where id = ?3
                            and (status is null or status not in ?4)
                        """,
                status,
                new Date(),
                runId,
                RunStatusTypes.terminalStates()
        );
        if (updated > 1) {
            throw new IllegalStateException("Multiple steps updated for stepId " + runId);
        }
        return updated == 1;
    }

    public int detachTransformer(Long transformerId) {
        return update("transformation = null where transformation.id = ?1", transformerId);
    }

    @Transactional
    public boolean updateStatusTransactional(Long runId, RunStatusTypes status, Float progress, String lastError) {
        int updated = update(
                """
                        status = ?1,
                        updatedAt = ?2,
                        lastError = ?3,
                        progress = ?4
                        where id = ?5
                            and (status is null or status not in ?6)
                        """,
                status,
                new Date(),
                lastError,
                progress,
                runId,
                RunStatusTypes.terminalStates()
        );
        if (updated > 1) {
            throw new IllegalStateException("Multiple steps updated for stepId " + runId);
        }
        return updated == 1;
    }


}
