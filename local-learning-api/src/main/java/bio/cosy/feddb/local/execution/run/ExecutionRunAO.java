package bio.cosy.feddb.local.execution.run;

import bio.cosy.feddb.core.execution.run.BaseExecutionRunAO;
import bio.cosy.feddb.local.api.cohort.patient.tools.PatientToolRunType;
import io.quarkus.panache.common.Page;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class ExecutionRunAO extends BaseExecutionRunAO<ExecutionRunEntity> {

    public List<ExecutionRunEntity> listToolRuns(Long cohortId, PatientToolRunType toolType, int max) {
        return find("cohort.id = ?1 and toolType = ?2 order by id desc", cohortId, toolType).page(Page.ofSize(max)).list();
    }

    public List<ExecutionRunEntity> listByCohort(Long cohortId) {
        return list("cohort.id", cohortId);
    }

    public List<ExecutionRunEntity> listByConnectorRun(Long connectorRunId) {
        return list("connectorRun.id = ?1 order by id", connectorRunId);
    }

    public Optional<ExecutionRunEntity> findByIdAndCohortIdOptional(Long runId, Long cohortId) {
        return find("id = ?1 and cohort.id = ?2", runId, cohortId).firstResultOptional();
    }

    public Optional<ExecutionRunEntity> findOfAttemptOptional(Long attemptId) {
        return find("from ExecutionRunEntity r where exists (from ExecutionAttemptEntity a where a.id = ?1 and a.step.run = r)", attemptId)
                .firstResultOptional();
    }
}
