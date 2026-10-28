package de.unihamburg.daibetes.execution.run;

import bio.cosy.feddb.core.execution.ExecutionLocation;
import bio.cosy.feddb.core.execution.ExecutionMode;
import bio.cosy.feddb.core.execution.ExecutionPurpose;
import bio.cosy.feddb.core.execution.run.BaseExecutionRunAO;
import bio.cosy.feddb.core.execution.run.types.RunStatusTypes;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
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

    @Transactional
    public boolean isAnalysisTransactional(Long runId) {
        return count("id = ?1 and purpose = ?2", runId, ExecutionPurpose.ANALYSIS) > 0;
    }

    public List<ExecutionRunEntity> listByIds(List<Long> runIds) {
        return list("id in ?1", runIds);
    }

    public Optional<ExecutionRunEntity> findTestRunOfAppOptional(Long runId, Long appId) {
        return find("from ExecutionRunEntity r where r.id = ?1 and r.purpose = ?2 and exists ("
                        + "from ExecutionRunStepEntity s where s.run = r and s.federatedAppVersion.federatedApp.id = ?3)",
                runId, ExecutionPurpose.TEST, appId).firstResultOptional();
    }

    public Optional<ExecutionRunEntity> findFederatedTestRunOptional(Long runId) {
        return find("id = ?1 and purpose = ?2 and mode = ?3", runId, ExecutionPurpose.TEST, ExecutionMode.FEDERATED).firstResultOptional();
    }

    public List<ExecutionRunEntity> listFederatedTrainingsOfProject(Long projectId) {
        return list("experiment.project.id = ?1 and mode = ?2 order by createdAt desc", projectId, ExecutionMode.FEDERATED);
    }

    public Optional<ExecutionRunEntity> findFederatedTrainingOfProjectOptional(Long runId, Long projectId) {
        return find("id = ?1 and mode = ?2 and experiment.project.id = ?3", runId, ExecutionMode.FEDERATED, projectId).firstResultOptional();
    }

    public List<ExecutionRunEntity> listPlatformTrainingsOfProject(Long projectId, boolean tests) {
        return list("experiment.project.id = ?1 and experiment.test = ?2 and location = ?3 order by createdAt desc",
                projectId, tests, ExecutionLocation.PLATFORM);
    }

    public Optional<ExecutionRunEntity> findPlatformTrainingOfProjectOptional(Long runId, Long projectId) {
        return find("id = ?1 and location = ?2 and experiment.project.id = ?3", runId, ExecutionLocation.PLATFORM, projectId)
                .firstResultOptional();
    }
}
