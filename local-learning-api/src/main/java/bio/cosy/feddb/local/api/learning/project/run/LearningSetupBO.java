package bio.cosy.feddb.local.api.learning.project.run;

import bio.cosy.feddb.core.api.app.config.ToolInputConfigDTO;
import bio.cosy.feddb.core.api.socket.FederatedLearningRelayInfoDTO;
import bio.cosy.feddb.core.api.socket.StartLearningClientRequestDTO;
import bio.cosy.feddb.core.execution.ExecutionMode;
import bio.cosy.feddb.core.execution.ExecutionPurpose;
import bio.cosy.feddb.core.execution.event.ExecutionEventPublisher;
import bio.cosy.feddb.core.execution.executor.ExecutionStartDTO;
import bio.cosy.feddb.core.execution.file.ExecutionFileDirection;
import bio.cosy.feddb.core.execution.orchestrator.WorkflowOrchestrator;
import bio.cosy.feddb.core.execution.run.dto.FederatedRunEnricher;
import bio.cosy.feddb.core.execution.run.dto.StartRunDTO;
import bio.cosy.feddb.core.execution.run.types.RunStatusTypes;
import bio.cosy.feddb.core.services.orch.dto.StartWorkflowNodeDTO;
import bio.cosy.feddb.local.api.cohort.ExternalDatabaseDTO;
import bio.cosy.feddb.local.api.cohort.TreSourceBO;
import bio.cosy.feddb.local.api.cohort.patient.export.PatientDataExportBO;
import bio.cosy.feddb.local.api.learning.project.LearningProjectAO;
import bio.cosy.feddb.local.api.learning.project.LearningProjectEntity;
import bio.cosy.feddb.local.api.learning.request.LearningRequestEntity;
import bio.cosy.feddb.local.api.workflow.WorkflowBO;
import bio.cosy.feddb.local.api.workflow.node.WorkflowNodeBO;
import bio.cosy.feddb.local.api.workflow.node.WorkflowNodeEntity;
import bio.cosy.feddb.local.config.FLNetClientConfig;
import bio.cosy.feddb.local.execution.attempt.ExecutionAttemptEntity;
import bio.cosy.feddb.local.execution.coordinator.ExecutionStepCoordinator;
import bio.cosy.feddb.local.execution.federation.ExecutionFederationAO;
import bio.cosy.feddb.local.execution.federation.ExecutionFederationEntity;
import bio.cosy.feddb.local.execution.file.ExecutionFileBO;
import bio.cosy.feddb.local.execution.run.ExecutionRunEntity;
import bio.cosy.feddb.local.execution.run.WorkflowExecutionBO;
import bio.cosy.feddb.local.execution.step.ExecutionRunStepEntity;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.commons.io.FileUtils;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@ApplicationScoped
public class LearningSetupBO {

    @Inject
    WorkflowExecutionBO workflowExecutionBO;

    @Inject
    ExecutionStepCoordinator stepCoordinator;

    @Inject
    ExecutionFileBO executionFileBO;

    @Inject
    ExecutionEventPublisher events;

    @Inject
    LearningProjectAO projectAO;

    @Inject
    ExecutionFederationAO federationAO;

    @Inject
    PatientDataExportBO exportBO;

    @Inject
    WorkflowBO workflowBO;

    @Inject
    WorkflowNodeBO workflowNodeBO;

    @Inject
    WorkflowOrchestrator orchestrator;

    @Inject
    FLNetClientConfig config;

    @Inject
    TreSourceBO treSourceBO;

    public String approve(LearningRequestEntity request) {
        LearningProjectEntity project = request.getProject();
        if (project.getRun() != null) {
            return project.getRun().getParticipantId();
        }
        ExecutionRunEntity run = workflowExecutionBO.createTransactional(
                new ExecutionStartDTO(ExecutionPurpose.TRAINING, project.getExecutionMode(), project.getName()), project.getWorkflow().getNodes());
        run.setParticipantId(UUID.randomUUID().toString());
        run.setKeycloakId(config.user().systemUserName());
        project.setRun(run);
        return run.getParticipantId();
    }

    public ExecutionRunStepEntity startTraining(LearningProjectEntity project, ExecutionRunEntity run, StartLearningClientRequestDTO request) {
        if (run.getParticipantId().equals(request.getCoordinatorId())) {
            project.setIsCoordinator(true);
        }
        if (Boolean.FALSE.equals(request.getModelCanBePublic())) {
            project.getRequest().setModelCanBePublic(false);
        }
        return launchNext(run);
    }

    public void prepareNextStep(ExecutionRunEntity run, FederatedLearningRelayInfoDTO relay) {
        ExecutionRunStepEntity next = launchNext(run);
        if (next != null && relay != null) {
            federationAO.applyRelay(next, relay);
        }
    }

    public void customizeStartup(ExecutionAttemptEntity attempt, StartRunDTO start) {
        ExecutionRunStepEntity step = attempt.getStep();
        LinkedHashMap<String, String> inputFilePaths = new LinkedHashMap<>();
        inputsOf(step).forEach(input -> inputFilePaths.put(input.getVariableName(), input.getFileName()));
        start.setInputFilePaths(inputFilePaths);
        if (step.getRun().getMode() == ExecutionMode.TRE) {
            // central training on this clinic, no relay
            start.setSupportFederatedLearning(false);
        }
        ExternalDatabaseDTO database = databaseOf(step);
        if (database != null && database.getHyperParams() != null) {
            // the client's values win over the project's
            LinkedHashMap<String, Object> hyperParams = new LinkedHashMap<>(start.getHyperParams() == null ? Map.of() : start.getHyperParams());
            hyperParams.putAll(database.getHyperParams());
            start.setHyperParams(hyperParams);
        }
        ExecutionFederationEntity federation = step.getFederation();
        if (federation == null || federation.getConfig() == null) {
            return;
        }
        if (federation.getConfig().getSuperlinkAddress() != null) {
            // Flower: this clinic is always a CLIENT, never the coordinator - a Flower step's
            // aggregator is always the platform's own attempt (see FederatedTrainingFlowerAggregatorBO),
            // never a clinic, so there is no relay-style coordinator slot for this to check.
            FederatedRunEnricher.enrichWithFlower(start, false, federation.getConfig());
            return;
        }
        // A clinic always trains on its own data, also when it coordinates.
        FederatedRunEnricher.enrichWithFederatedRelay(start, federation.getConfig(), Boolean.TRUE.equals(federation.getStartAggregator()), false);
    }

    /**
     * Puts the clinic's data into the volume of a step that reads it: the first one or one the workflow feeds.
     */
    public void stageInputs(ExecutionAttemptEntity attempt) {
        ExecutionRunStepEntity step = attempt.getStep();
        if (!readsProjectData(step)) {
            return;
        }
        LearningProjectEntity project = projectAO.findByRunId(step.getRun().getId());
        if (project.getTreSource() != null) {
            // the app reads the external database itself
            return;
        }
        Path data = exportBO.exportDataForLearning(project.getExportConfig(), project.getQuery().getId(), project.getRequest().getId());
        try {
            inputsOf(step).forEach(input -> orchestrator.uploadFilesToVolume(step.getRun().getId(), attempt.getId(), data, input.getFileName()));
            executionFileBO.storeAndLinkFiles(attempt, List.of(data.toFile()), ExecutionFileDirection.INPUT);
            Log.infof("Staged the data of project %d into attempt %d", project.getId(), attempt.getId());
        } finally {
            FileUtils.deleteQuietly(data.toFile());
        }
    }

    public void customizeStart(ExecutionAttemptEntity attempt, StartWorkflowNodeDTO start) {
        ExternalDatabaseDTO database = databaseOf(attempt.getStep());
        if (database == null) {
            return;
        }
        TreLearningHelper.connect(database, start);
    }

    private ExternalDatabaseDTO databaseOf(ExecutionRunStepEntity step) {
        if (!readsProjectData(step)) {
            return null;
        }
        LearningProjectEntity project = projectAO.findByRunId(step.getRun().getId());
        if (project == null || project.getTreSource() == null) {
            return null;
        }
        ExternalDatabaseDTO database = treSourceBO.findDatabaseTransactional(project.getTreSource().getId())
                .orElseThrow(() -> new IllegalStateException("External database " + project.getTreSource().getName() + " was removed"));
        TreLearningHelper.checkTool(database, step);
        return database;
    }

    // The step after the current one, or the first; its app starts and waits until every clinic is ready for it.
    private ExecutionRunStepEntity launchNext(ExecutionRunEntity run) {
        List<ExecutionRunStepEntity> steps = run.getStepsInExecutionOrder();
        int index = run.getCurrentStep().map(steps::indexOf).orElse(-1) + 1;
        if (index >= steps.size()) {
            Log.infof("Run %d has no step after the current one", run.getId());
            return null;
        }
        ExecutionRunStepEntity step = steps.get(index);
        if (step.getStatus() != RunStatusTypes.PENDING) {
            Log.infof("Step %d of run %d was launched already", step.getId(), run.getId());
            return step;
        }
        run.begin(RunStatusTypes.RUNNING);
        stepCoordinator.launchTrial(step, 0, Map.of());
        events.stepChanged(step.getId());
        return step;
    }

    // The first step reads the clinic's data, a later one only if the workflow feeds it.
    private boolean readsProjectData(ExecutionRunStepEntity step) {
        WorkflowNodeEntity node = step.getWorkflowNode();
        return step.isFirstInExecutionOrder() || workflowBO.entityToDto(node.getWorkflow()).feedsInputInto(node.getNodeId());
    }

    private List<ToolInputConfigDTO> inputsOf(ExecutionRunStepEntity step) {
        return workflowNodeBO.toDetailDto(step.getWorkflowNode()).getAppInputConfig();
    }

}