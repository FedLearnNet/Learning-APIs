package de.unihamburg.daibetes.api.runs.test.federated;

import bio.cosy.feddb.core.api.run.RunStatusTypes;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;

@ApplicationScoped
public class FederatedTestRunAO implements PanacheRepository<FederatedTestRunEntity> {

    public List<FederatedTestRunEntity> findByAppId(Long id) {
        return list("federatedAppVersion.federatedApp.id = ?1",
                Sort.by("createdAt", Sort.Direction.Descending),
                id);
    }

    public List<FederatedTestRunEntity> findAllRunningByAppId(Long id) {
        return list("federatedAppVersion.federatedApp.id = ?1 and status not in ?2",
                id,
                List.of(RunStatusTypes.FINISHED, RunStatusTypes.ERROR));
    }

    public boolean updateRun(Long runId,
                             RunStatusTypes status,
                             String error,
                             Integer currentRound) {
        int updated = update(
                """
                        status = ?1,
                        error = ?2,
                        currentRound = ?3,
                        updatedAt = ?4
                        where id = ?5
                        """,
                status,
                error,
                currentRound,
                new Date(),
                runId
        );
        if (updated > 1) {
            throw new IllegalStateException("Multiple federated runs updated for runId " + runId);
        }
        return updated == 1;
    }

    public boolean finishRun(Long runId,
                             String error,
                             Integer currentRound) {
        int updated = update(
                """
                        status = ?1,
                        error = ?2,
                        currentRound = ?3,
                        updatedAt = ?4
                        where id = ?5
                        """,
                RunStatusTypes.FINISHED,
                error,
                currentRound,
                new Date(),
                runId
        );
        if (updated > 1) {
            throw new IllegalStateException("Multiple federated runs updated for runId " + runId);
        }
        return updated == 1;
    }

    public boolean uploadOutput(Long runId,
                                LinkedHashMap<String, Object> outputData) {
        int updated = update(
                """
                        outputData = ?1,
                        updatedAt = ?2
                        where id = ?3
                        """,
                outputData,
                new Date(),
                runId
        );
        if (updated > 1) {
            throw new IllegalStateException("Multiple federated runs updated for runId " + runId);
        }
        return updated == 1;
    }
}
