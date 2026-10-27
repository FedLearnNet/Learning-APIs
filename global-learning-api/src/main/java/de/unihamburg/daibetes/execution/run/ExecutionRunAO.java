package de.unihamburg.daibetes.execution.run;

import bio.cosy.feddb.core.execution.ExecutionMode;
import bio.cosy.feddb.core.execution.ExecutionPurpose;
import bio.cosy.feddb.core.execution.run.BaseExecutionRunAO;
import bio.cosy.feddb.core.execution.run.types.RunStatusTypes;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.NotFoundException;

import java.util.List;
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

    public List<ExecutionRunEntity> listTestRunsOfApp(Long appId, ExecutionMode mode, boolean experiments) {
        return list("from ExecutionRunEntity r where r.purpose = ?1 and r.mode = ?2 and r.experiment is "
                        + (experiments ? "not null" : "null") + " and exists ("
                        + "from ExecutionRunStepEntity s where s.run = r and s.federatedAppVersion.federatedApp.id = ?3) "
                        + "order by r.createdAt desc",
                ExecutionPurpose.TEST, mode, appId);
    }

    public List<ExecutionRunEntity> listOpenTestRunsOfApp(Long appId) {
        return list("from ExecutionRunEntity r where r.purpose = ?1 and r.status not in ?2 and exists ("
                        + "from ExecutionRunStepEntity s where s.run = r and s.federatedAppVersion.federatedApp.id = ?3)",
                ExecutionPurpose.TEST, List.of(RunStatusTypes.FINISHED, RunStatusTypes.STOPPED, RunStatusTypes.ERROR), appId);
    }
}
