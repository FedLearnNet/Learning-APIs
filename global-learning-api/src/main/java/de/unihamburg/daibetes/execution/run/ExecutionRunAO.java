package de.unihamburg.daibetes.execution.run;

import bio.cosy.feddb.core.execution.run.BaseExecutionRunAO;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.NotFoundException;

import java.util.Optional;

@ApplicationScoped
public class ExecutionRunAO extends BaseExecutionRunAO<ExecutionRunEntity> {

    public Optional<ExecutionRunEntity> findByIdAndKeycloakIdOptional(Long runId, String keycloakId) {
        return find("id = ?1 and keycloakId = ?2", runId, keycloakId).firstResultOptional();
    }

    public ExecutionRunEntity findByIdAndKeycloakId(Long runId, String keycloakId) {
        return findByIdAndKeycloakIdOptional(runId, keycloakId)
                .orElseThrow(() -> new NotFoundException("Execution run not found"));
    }

}
