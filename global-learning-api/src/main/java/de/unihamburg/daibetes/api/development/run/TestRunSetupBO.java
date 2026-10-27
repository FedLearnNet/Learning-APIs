package de.unihamburg.daibetes.api.development.run;

import bio.cosy.feddb.core.execution.ExecutionMode;
import bio.cosy.feddb.core.execution.ExecutionPurpose;
import bio.cosy.feddb.core.execution.executor.ExecutionStartDTO;
import bio.cosy.feddb.core.execution.hyperparam.config.HyperParamSearchDTO;
import bio.cosy.feddb.core.execution.run.dto.StartRunDTO;
import bio.cosy.feddb.core.execution.run.types.FederatedParticipantType;
import bio.cosy.feddb.core.execution.run.types.RunStatusTypes;
import de.unihamburg.daibetes.api.app.version.FederatedAppVersionEntity;
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
import de.unihamburg.daibetes.execution.run.ExecutionRunDetailMapper;
import de.unihamburg.daibetes.execution.run.ExecutionRunEntity;
import de.unihamburg.daibetes.execution.run.WorkflowExecutionBO;
import de.unihamburg.daibetes.execution.step.ExecutionRunStepEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

    public ExecutionRunEntity startTest(FederatedAppVersionEntity version, TestRunCreateDTO create, String keycloakId) {
        LinkedHashMap<String, Object> inputs = create.getProjectId() != null
                ? projectBO.dummyTrainingInputsOf(create.getProjectId(), create.getFederatedAppVersionId())
                : new LinkedHashMap<>();
        LinkedHashMap<String, String> inputFilePaths = create.getProjectId() != null ? null : create.getInputFilePaths();
        ExecutionRunEntity run = workflowExecutionBO.executeSingleTransactional(
                new ExecutionStartDTO(ExecutionPurpose.TEST, ExecutionMode.SINGLE, version.getFederatedApp().getName()),
                HyperParamSearchDTO.fixed(create.getHyperParams() != null ? create.getHyperParams() : new LinkedHashMap<>()),
                inputs,
                step -> {
                    step.setFederatedAppVersion(version);
                    step.setInputFilePaths(inputFilePaths);
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
            // Before its attempt, which points to it.
            participantAO.persist(joined);
            ExecutionAttemptEntity attempt = stepCoordinator.launchTrial(step, attempts.size(),
                    participant.getHyperParams() != null ? participant.getHyperParams() : Map.of());
            attempt.federationOrNew().setParticipant(joined);
            attempts.add(attempt);
        }
        // The aggregator stands for the step, it holds the output of the run.
        step.setSelectedAttempt(attempts.stream()
                .filter(attempt -> attempt.getFederation().getParticipant().getRole() == FederatedParticipantType.AGGREGATOR)
                .findFirst()
                .orElse(attempts.getFirst()));
        return run;
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
