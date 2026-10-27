package de.unihamburg.daibetes.api.project.training.platform;

import de.unihamburg.daibetes.api.project.membership.ProjectMembershipAO;
import de.unihamburg.daibetes.execution.attempt.ExecutionAttemptEntity;
import de.unihamburg.daibetes.execution.run.ExecutionRunAO;
import de.unihamburg.daibetes.execution.run.ExecutionRunDetailDTO;
import de.unihamburg.daibetes.execution.run.ExecutionRunDetailMapper;
import de.unihamburg.daibetes.execution.run.ExecutionRunEntity;
import de.unihamburg.daibetes.execution.run.WorkflowExecutionBO;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;

import java.util.LinkedHashMap;
import java.util.List;

/**
 * Training of a project's workflow on the platform, or a test of it on dummy data: an experiment of the project
 * with a TRAINING run whose steps are the workflow's nodes. Runs are addressed by their id; the work goes to the
 * setup, progress and cleanup BOs.
 */
@ApplicationScoped
public class PlatformTrainingBO {

    @Inject
    ProjectMembershipAO membershipAO;

    @Inject
    ExecutionRunAO runAO;

    @Inject
    ExecutionRunDetailMapper mapper;

    @Inject
    WorkflowExecutionBO workflowExecutionBO;

    @Inject
    PlatformTrainingSetupBO setupBO;

    @Inject
    PlatformTrainingProgressBO progressBO;

    @Inject
    PlatformTrainingCleanupBO cleanupBO;

    /** The trainings of a project, newest first; its tests are not among them. */
    @Transactional
    public List<ExecutionRunDetailDTO> listTransactional(Long projectId, String keycloakId) {
        membershipAO.checkProjectAndUser(projectId, keycloakId);
        return runAO.listPlatformTrainingsOfProject(projectId, false).stream().map(run -> mapper.mapRunEntityToDetailDTO(run, false)).toList();
    }

    /** The run; with messages every attempt carries its logs and metrics. */
    @Transactional
    public ExecutionRunDetailDTO findRunTransactional(Long projectId, Long runId, String keycloakId, boolean withMessages) {
        membershipAO.checkProjectAndUser(projectId, keycloakId);
        return mapper.mapRunEntityToDetailDTO(runOf(projectId, runId), withMessages);
    }

    /** The latest test of a project; null if it was never tested. */
    @Transactional
    public ExecutionRunDetailDTO findTestTransactional(Long projectId, String keycloakId) {
        membershipAO.checkProjectAndUser(projectId, keycloakId);
        return runAO.listPlatformTrainingsOfProject(projectId, true).stream().findFirst()
                .map(run -> mapper.mapRunEntityToDetailDTO(run, false))
                .orElse(null);
    }

    /** Sets up a training, started later. */
    @Transactional
    public ExecutionRunDetailDTO createTransactional(Long projectId, CreateProjectLocalExperimentDTO create, String keycloakId) {
        membershipAO.checkProjectAndUser(projectId, keycloakId);
        return mapper.mapRunEntityToDetailDTO(setupBO.createRun(projectId, create.getName(), create.getDescription(), false, keycloakId), false);
    }

    /** Tests the project's workflow on dummy data; replaces the test before. */
    @Transactional
    public ExecutionRunDetailDTO createTestAndStartTransactional(Long projectId, String keycloakId) {
        membershipAO.checkProjectAndUser(projectId, keycloakId);
        cleanupBO.deleteTests(projectId);
        ExecutionRunEntity run = setupBO.createRun(projectId, "Test", null, true, keycloakId);
        workflowExecutionBO.startTransactional(run.getId());
        return mapper.mapRunEntityToDetailDTO(run, false);
    }

    @Transactional
    public ExecutionRunDetailDTO startTransactional(Long projectId, Long runId, String keycloakId) {
        membershipAO.checkProjectAndUser(projectId, keycloakId);
        return mapper.mapRunEntityToDetailDTO(workflowExecutionBO.startTransactional(runOf(projectId, runId).getId()), false);
    }

    @Transactional
    public ExecutionRunDetailDTO stopTransactional(Long projectId, Long runId, String keycloakId) {
        membershipAO.checkProjectAndUser(projectId, keycloakId);
        return mapper.mapRunEntityToDetailDTO(cleanupBO.stop(runOf(projectId, runId)), false);
    }

    public LinkedHashMap<String, String> inputFilePathsOf(ExecutionAttemptEntity attempt) {
        return setupBO.inputFilePathsOf(attempt);
    }

    public void stageInputs(ExecutionAttemptEntity attempt) {
        setupBO.stageInputs(attempt);
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void keepOutputsTransactional(Long stepId) {
        progressBO.keepOutputs(stepId);
    }

    private ExecutionRunEntity runOf(Long projectId, Long runId) {
        return runAO.findPlatformTrainingOfProjectOptional(runId, projectId)
                .orElseThrow(() -> new NotFoundException("Training " + runId + " not found"));
    }
}
