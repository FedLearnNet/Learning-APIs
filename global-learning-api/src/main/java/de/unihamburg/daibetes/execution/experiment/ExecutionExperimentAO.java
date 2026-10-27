package de.unihamburg.daibetes.execution.experiment;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class ExecutionExperimentAO implements PanacheRepository<ExecutionExperimentEntity> {

    public Optional<ExecutionExperimentEntity> findByGlobalUniqueIdOptional(String globalUniqueId) {
        return find("globalUniqueId", globalUniqueId).firstResultOptional();
    }

    public Optional<ExecutionExperimentEntity> findByGlobalUniqueIdLockedOptional(String globalUniqueId) {
        return find("globalUniqueId", globalUniqueId).withLock(LockModeType.PESSIMISTIC_WRITE).firstResultOptional();
    }

    public ExecutionExperimentEntity lock(ExecutionExperimentEntity experiment) {
        getEntityManager().lock(experiment, LockModeType.PESSIMISTIC_WRITE);
        return experiment;
    }

    public List<ExecutionExperimentEntity> listTestsOfProject(Long projectId) {
        return list("project.id = ?1 and test = true", projectId);
    }

    public Optional<ExecutionExperimentEntity> findOfAppWithRunsOptional(Long experimentId, Long appId) {
        return find("id = ?1 and federatedAppVersion.federatedApp.id = ?2 and runs is not empty", experimentId, appId).firstResultOptional();
    }
}
