package de.unihamburg.daibetes.api.development.run;

import bio.cosy.feddb.core.api.app.config.ToolConfigModeType;
import bio.cosy.feddb.core.api.app.config.ToolConfigsDTO;
import bio.cosy.feddb.core.api.app.config.ToolInputConfigDTO;
import bio.cosy.feddb.core.execution.ExecutionMode;
import bio.cosy.feddb.core.execution.file.ExecutionFileDirection;
import bio.cosy.feddb.core.execution.orchestrator.WorkflowOrchestrator;
import bio.cosy.feddb.core.execution.ExecutionPurpose;
import bio.cosy.feddb.core.execution.executor.ExecutionStartDTO;
import bio.cosy.feddb.core.execution.hyperparam.config.HyperParamSearchDTO;
import bio.cosy.feddb.core.execution.run.dto.StartRunDTO;
import bio.cosy.feddb.core.execution.run.types.FederatedParticipantType;
import bio.cosy.feddb.core.execution.run.types.RunStatusTypes;
import de.unihamburg.daibetes.api.app.config.FederatedAppConfigBO;
import de.unihamburg.daibetes.api.app.version.FederatedAppVersionEntity;
import de.unihamburg.daibetes.api.file.FileBO;
import de.unihamburg.daibetes.api.file.FileEntity;
import de.unihamburg.daibetes.api.development.run.federated.FederatedParticipantCreateDTO;
import de.unihamburg.daibetes.api.development.run.federated.FederatedTestRunCreateDTO;
import de.unihamburg.daibetes.api.project.ProjectBO;
import de.unihamburg.daibetes.execution.attempt.ExecutionAttemptEntity;
import de.unihamburg.daibetes.execution.attempt.ExecutionAttemptLifecycle;
import de.unihamburg.daibetes.execution.coordinator.ExecutionStepCoordinator;
import de.unihamburg.daibetes.execution.experiment.ExecutionExperimentAO;
import de.unihamburg.daibetes.execution.experiment.ExecutionExperimentDTO;
import de.unihamburg.daibetes.execution.experiment.ExecutionExperimentEntity;
import de.unihamburg.daibetes.execution.experiment.ExecutionExperimentMapper;
import de.unihamburg.daibetes.execution.federation.ExecutionFederationEntity;
import de.unihamburg.daibetes.execution.federation.ExecutionFederationMapper;
import de.unihamburg.daibetes.execution.federation.ExecutionParticipantAO;
import de.unihamburg.daibetes.execution.federation.ExecutionParticipantEntity;
import de.unihamburg.daibetes.execution.file.ExecutionFileBO;
import de.unihamburg.daibetes.execution.run.ExecutionRunDetailMapper;
import de.unihamburg.daibetes.execution.run.ExecutionRunEntity;
import de.unihamburg.daibetes.execution.run.WorkflowExecutionBO;
import de.unihamburg.daibetes.execution.step.ExecutionRunStepEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import io.quarkus.logging.Log;
import jakarta.ws.rs.BadRequestException;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@ApplicationScoped
public class TestRunSetupBO {

    @Inject
    ProjectBO projectBO;

    @Inject
    WorkflowExecutionBO workflowExecutionBO;

    @Inject
    ExecutionStepCoordinator stepCoordinator;

    @Inject
    ExecutionAttemptLifecycle lifecycle;

    @Inject
    ExecutionRunDetailMapper mapper;

    @Inject
    ExecutionExperimentAO experimentAO;

    @Inject
    ExecutionParticipantAO participantAO;

    @Inject
    ExecutionExperimentMapper experimentMapper;

    @Inject
    ExecutionFederationMapper federationMapper;

    @Inject
    FederatedAppConfigBO appConfigBO;

    @Inject
    FileBO fileBO;

    @Inject
    ExecutionFileBO executionFileBO;

    @Inject
    WorkflowOrchestrator orchestrator;

    public ExecutionRunEntity startTest(FederatedAppVersionEntity version, TestRunCreateDTO create, String keycloakId) {
        boolean fromProject = create.getProjectId() != null;
        LinkedHashMap<String, Object> inputs = new LinkedHashMap<>();
        if (fromProject) {
            inputs = projectBO.dummyTrainingInputsOf(create.getProjectId(), create.getFederatedAppVersionId());
        } else if (create.getInputs() != null) {
            inputs = create.getInputs();
        }
        LinkedHashMap<String, Object> hyperParams = new LinkedHashMap<>();
        if (create.getHyperParams() != null) {
            hyperParams = create.getHyperParams();
        }
        ExecutionRunEntity run = workflowExecutionBO.executeSingleTransactional(
                new ExecutionStartDTO(ExecutionPurpose.TEST, ExecutionMode.SINGLE, version.getFederatedApp().getName()),
                HyperParamSearchDTO.fixed(hyperParams),
                inputs,
                step -> {
                    step.setFederatedAppVersion(version);
                    if (!fromProject) {
                        step.setInputFilePaths(create.getInputFilePaths());
                    }
                });
        run.setKeycloakId(keycloakId);
        return run;
    }

    public ExecutionRunEntity startExperiment(FederatedAppVersionEntity version, CreateExperimentDTO create, String keycloakId) {
        ExecutionRunEntity run = workflowExecutionBO.executeSingleTransactional(
                new ExecutionStartDTO(ExecutionPurpose.TEST, ExecutionMode.SINGLE, create.getName()),
                create.getSearch(), mapper.mapJsonToFields(create.getInputData()),
                step -> {
                    step.setFederatedAppVersion(version);
                    step.setInputFilePaths(create.getInputFilePaths());
                });
        run.setKeycloakId(keycloakId);
        ExecutionExperimentEntity experiment = experimentMapper.createDtoToEntity(create, keycloakId, version);
        experimentAO.persist(experiment);
        // The run owns the link, the experiment only lists it.
        run.setExperiment(experiment);
        experiment.getRuns().add(run);
        return run;
    }

    public ExecutionExperimentDTO updateExperiment(ExecutionExperimentEntity experiment, ExecutionExperimentDTO update) {
        experimentMapper.updateEntity(update, experiment);
        return experimentMapper.entityToDto(experiment);
    }

    public ExecutionRunEntity startFederatedTest(FederatedAppVersionEntity version, FederatedTestRunCreateDTO create, String keycloakId) {
        if (create.getParticipants() == null || create.getParticipants().isEmpty()) {
            throw new BadRequestException("A federated test run needs participants");
        }
        if (create.getParticipants().stream().anyMatch(p -> p.getParticipantId() == null || p.getParticipantId().isBlank())
                || create.getParticipants().stream().map(FederatedParticipantCreateDTO::getParticipantId).distinct().count() != create.getParticipants().size()
                || create.getParticipants().stream().filter(p -> p.getRole() == FederatedParticipantType.AGGREGATOR).count() != 1
                || create.getParticipants().stream().noneMatch(p -> p.getRole() == FederatedParticipantType.CLIENT)) {
            throw new BadRequestException("A federated test needs unique participant IDs, one aggregator and at least one client");
        }
        if (create.getAggregatorParticipantId() != null && create.getParticipants().stream().noneMatch(p ->
                p.getRole() == FederatedParticipantType.AGGREGATOR && p.getParticipantId().equals(create.getAggregatorParticipantId()))) {
            throw new BadRequestException("The aggregator host must be one of the configured participants");
        }
        ExecutionFederationEntity federation = federationMapper.createDtoToEntity(create);
        ExecutionRunEntity run = workflowExecutionBO.executeSingleTransactional(
                new ExecutionStartDTO(ExecutionPurpose.TEST, ExecutionMode.FEDERATED, version.getFederatedApp().getName()), null,
                new LinkedHashMap<>(),
                step -> {
                    step.setFederatedAppVersion(version);
                    federation.setStep(step);
                    step.setFederation(federation);
                });
        run.setKeycloakId(keycloakId);
        run.begin(RunStatusTypes.RUNNING);
        ExecutionRunStepEntity step = run.getSteps().getFirst();

        List<ExecutionAttemptEntity> attempts = new ArrayList<>();
        for (FederatedParticipantCreateDTO participant : create.getParticipants()) {
            ExecutionParticipantEntity joined = run.addParticipant(federationMapper.createDtoToEntity(participant));
            joined.setCoordinator(participant.getParticipantId().equals(create.getAggregatorParticipantId()));
            // Before its attempt, which points to it.
            participantAO.persist(joined);
            ExecutionAttemptEntity attempt = stepCoordinator.launchTrial(step, attempts.size(),
                    participant.getHyperParams() != null ? participant.getHyperParams() : Map.of());
            attempt.federationOrNew().setParticipant(joined);
            attempts.add(attempt);
        }
        if (create.getProjectId() != null) {
            addProjectData(version, create.getProjectId(), attempts, keycloakId);
        }
        // The aggregator stands for the step, it holds the output of the run.
        step.setSelectedAttempt(attempts.stream()
                .filter(attempt -> attempt.getFederation().getParticipant().getRole() == FederatedParticipantType.AGGREGATOR)
                .findFirst()
                .orElse(attempts.getFirst()));
        return run;
    }

    // Dummy data of the project's export for participants without input files, its clients would train on its export
    private void addProjectData(FederatedAppVersionEntity version, Long projectId, List<ExecutionAttemptEntity> attempts, String keycloakId) {
        ToolInputConfigDTO input = trainingInputOf(version);
        File csv;
        try {
            csv = Files.writeString(Files.createTempFile("project-" + projectId + "-", ".csv"), projectBO.exportTestCsvOf(projectId, 100, keycloakId)).toFile();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not write the dummy data of project " + projectId, e);
        }
        try {
            FileEntity data = fileBO.createEntity(csv, input.getFileName(), keycloakId);
            for (ExecutionAttemptEntity attempt : attempts) {
                ExecutionParticipantEntity participant = attempt.getFederation().getParticipant();
                if (participant.getInputFilePaths() != null && !participant.getInputFilePaths().isEmpty()) {
                    continue;
                }
                String path = executionFileBO.linkInput(attempt, Map.of(input.getVariableName(), data)).getFirst().getFileNameWithExtension();
                participant.setInputFilePaths(new LinkedHashMap<>(Map.of(input.getVariableName(), path)));
            }
        } finally {
            csv.delete();
        }
    }

    private ToolInputConfigDTO trainingInputOf(FederatedAppVersionEntity version) {
        ToolConfigsDTO config = appConfigBO.findByAppVersionId(version.getId());
        return Optional.ofNullable(config).map(ToolConfigsDTO::getInput).orElse(List.of()).stream()
                .filter(input -> ToolConfigModeType.isTraining(input.getMode()) && input.getVariableName() != null)
                .findFirst()
                .orElseThrow(() -> new BadRequestException("App version " + version.getId() + " has no training input for the project's data"));
    }

    /** Puts the participants' input files into the volume of the test engine serving them, where the engine stages them from. */
    public void stageOnEngine(ExecutionRunEntity engine, List<ExecutionAttemptEntity> participants) {
        // an engine runs as the single attempt of its run, the volume is that of its container
        ExecutionAttemptEntity engineAttempt = engine.getSteps().getFirst().getAttempts().stream().findFirst().orElseThrow();
        participants.forEach(participant -> executionFileBO.loadForAttempt(participant.getId(), ExecutionFileDirection.INPUT)
                .forEach((path, content) -> orchestrator.uploadFilesToVolume(engine.getId(), engineAttempt.getId(), content, path)));
        Log.infof("Staged the input files of %d participants on test engine %d", participants.size(), engine.getId());
    }

    public StartRunDTO startupOf(ExecutionAttemptEntity attempt) {
        StartRunDTO start = lifecycle.startupOfTransactional(attempt.getId());
        mapper.mapAppVersionToStartRunDTO(attempt.getStep().getFederatedAppVersion(), start);
        return start;
    }

    public StartRunDTO federatedStartupOf(ExecutionRunStepEntity step) {
        return federationMapper.stepToFederatedStartRunDTO(step);
    }
}
