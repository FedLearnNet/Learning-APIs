package de.unihamburg.daibetes.api.development.engine;

import bio.cosy.feddb.core.execution.ExecutionMode;
import bio.cosy.feddb.core.execution.ExecutionPurpose;
import bio.cosy.feddb.core.execution.event.ExecutionEventPublisher;
import bio.cosy.feddb.core.execution.executor.ExecutionStartDTO;
import bio.cosy.feddb.core.execution.failure.ExecutionFailure;
import bio.cosy.feddb.core.execution.failure.FailureCategory;
import bio.cosy.feddb.core.execution.hyperparam.config.HyperParamSearchDTO;
import bio.cosy.feddb.core.execution.run.dto.UpdateRunDTO;
import bio.cosy.feddb.core.execution.run.types.AppConnectionTypesEnum;
import bio.cosy.feddb.core.execution.run.types.RunStatusTypes;
import bio.cosy.feddb.core.security.ToolApiKeyService;
import bio.cosy.feddb.core.security.Scope;
import de.unihamburg.daibetes.api.app.FederatedAppAO;
import de.unihamburg.daibetes.api.app.version.FederatedAppVersionAO;
import de.unihamburg.daibetes.api.app.version.FederatedAppVersionEntity;
import de.unihamburg.daibetes.api.development.run.TestRunBO;
import de.unihamburg.daibetes.api.development.testembed.TestEmbedConnections;
import de.unihamburg.daibetes.execution.attempt.ExecutionAttemptAO;
import de.unihamburg.daibetes.execution.attempt.ExecutionAttemptEntity;
import de.unihamburg.daibetes.execution.attempt.ExecutionAttemptLifecycle;
import de.unihamburg.daibetes.execution.run.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class TestEngineRunBO {

    @Inject
    ExecutionRunAO runAO;

    @Inject
    ExecutionAttemptAO attemptAO;

    @Inject
    ExecutionAttemptLifecycle lifecycle;

    @Inject
    ExecutionRunDetailMapper runMapper;

    @Inject
    WorkflowExecutionBO workflowExecutionBO;

    @Inject
    ExecutionEventPublisher events;

    @Inject
    FederatedAppVersionAO versionAO;

    @Inject
    TestRunBO testRunBO;

    @Inject
    TestEmbedConnections connections;

    @Inject
    ToolApiKeyService toolApiKeyService;

    @Inject
    FederatedAppAO appAO;

    @Transactional
    public String bootstrapCredentialTransactional(Long appId, String keycloakId) {
        testRunBO.requireAuthor(appId, keycloakId);
        appAO.findByIdOptional(appId, LockModeType.PESSIMISTIC_WRITE).orElseThrow(NotFoundException::new);
        if (runAO.findConnectingEngineOfApp(appId).isPresent()) {
            throw new BadRequestException("Stop the hosted test engine before connecting locally");
        }
        return toolApiKeyService.issue(Scope.TEST_EMBED_APP, appId, null);
    }

    @Transactional
    public ExecutionRunDetailDTO startTransactional(Long appId, TestEngineCreateDTO create, String keycloakId) {
        testRunBO.requireAuthor(appId, keycloakId);
        appAO.findByIdOptional(appId, LockModeType.PESSIMISTIC_WRITE).orElseThrow(NotFoundException::new);
        FederatedAppVersionEntity version = versionAO.findLastVersionByAppIdOptional(appId)
                .filter(FederatedAppVersionEntity::hasImage)
                .orElseThrow(() -> new BadRequestException("The latest version has no image yet"));
        TestEngineMode mode = create.getMode();
        if (mode.connects()) {
            if (runAO.findConnectingEngineOfApp(appId).isPresent()) {
                throw new BadRequestException("A test engine already runs for this tool");
            }
            if (connections.of(appId, AppConnectionTypesEnum.APP).findAny().isPresent()) {
                throw new BadRequestException("Stop the local engine of this tool first");
            }
        } else if (runAO.existsOpenSelfTestOfApp(appId)) {
            throw new BadRequestException("A self-test already runs for this tool");
        }

        String name = version.getFederatedApp().getName() + " v" + version.getVersionFormatted();
        ExecutionStartDTO start = new ExecutionStartDTO(ExecutionPurpose.TEST_HOSTING, ExecutionMode.SINGLE, name);
        HyperParamSearchDTO search = HyperParamSearchDTO.fixed(new LinkedHashMap<>());
        LinkedHashMap<String, Object> inputs = new LinkedHashMap<>();
        ExecutionRunEntity engine = workflowExecutionBO.executeSingleTransactional(start, search, inputs,
                step -> step.setFederatedAppVersion(version));
        engine.setKeycloakId(keycloakId);
        engine.setEngineMode(mode);

        if (mode == TestEngineMode.SINGLE_RUN) {
            if (create.getTestRun() == null) {
                throw new BadRequestException("A single run needs its test run");
            }
            create.getTestRun().setFederatedAppVersionId(version.getId());
            // Stays pending until the engine connects and the launcher hands it over
            Long runId = testRunBO.startTestTransactional(appId, create.getTestRun(), keycloakId);
            runAO.findById(runId).setTestEngine(engine);
        }
        return runMapper.mapRunEntityToDetailDTO(engine, false);
    }

    @Transactional
    public ExecutionRunDetailDTO stopTransactional(Long appId, Long runId, String keycloakId) {
        testRunBO.requireAuthor(appId, keycloakId);
        runAO.findEngineOfApp(runId, appId).orElseThrow(NotFoundException::new);
        return runMapper.mapRunEntityToDetailDTO(workflowExecutionBO.stopTransactional(runId), false);
    }

    @Transactional
    public List<ExecutionRunDetailDTO> listTransactional(Long appId, String keycloakId) {
        testRunBO.requireAuthor(appId, keycloakId);
        return runAO.listEnginesOfApp(appId).stream().map(run -> runMapper.mapRunEntityToDetailDTO(run, false)).toList();
    }

    @Transactional
    public void markConnectedTransactional(Long appId) {
        connectingAttemptIdOf(appId).ifPresent(lifecycle::markRunningTransactional);
    }

    @Transactional
    public void markDisconnectedTransactional(Long appId) {
        connectingAttemptIdOf(appId).ifPresent(attemptId -> {
            UpdateRunDTO closed = new UpdateRunDTO();
            closed.setStatus(RunStatusTypes.ERROR);
            closed.setError("The engine closed its connection");
            lifecycle.updateTransactional(attemptId, closed);
        });
    }

    @Transactional
    public boolean settleTransactional(Long attemptId, Integer exitCode, boolean timedOut) {
        ExecutionAttemptEntity attempt = attemptAO.findByIdWrite(attemptId).orElse(null);
        if (attempt == null || attempt.hasEnded() || attempt.getStep().getRun().getPurpose() != ExecutionPurpose.TEST_HOSTING) {
            return true;
        }
        boolean selfTest = !attempt.getStep().getRun().getEngineMode().connects();
        if (exitCode != null) {
            if (selfTest && exitCode == 0) {
                end(attempt, RunStatusTypes.FINISHED, null, exitCode);
            } else if (selfTest) {
                end(attempt, RunStatusTypes.ERROR, new ExecutionFailure(FailureCategory.TOOL, "The self-test exited with code " + exitCode), exitCode);
            } else {
                end(attempt, RunStatusTypes.ERROR, new ExecutionFailure(FailureCategory.TOOL, "The container exited with code " + exitCode), exitCode);
            }
            return true;
        }
        if (!selfTest && attempt.getStatus() == RunStatusTypes.RUNNING) {
            return true;
        }
        if (!timedOut) {
            return false;
        }
        if (selfTest) {
            end(attempt, RunStatusTypes.ERROR, new ExecutionFailure(FailureCategory.TOOL, "The self-test did not end in time"), null);
        } else {
            end(attempt, RunStatusTypes.ERROR, new ExecutionFailure(FailureCategory.INFRASTRUCTURE, "The container did not connect in time"), null);
        }
        return true;
    }

    // Whoever ended the engine's attempt, its key and a single run waiting on it go too
    @Transactional
    public void cleanUpTransactional(Long attemptId) {
        toolApiKeyService.revokeExecution(attemptId);
        ExecutionAttemptEntity attempt = attemptAO.findByIdOptional(attemptId).orElse(null);
        if (attempt == null) {
            return;
        }
        ExecutionRunEntity engine = attempt.getStep().getRun();
        if (engine.getEngineMode() == TestEngineMode.SINGLE_RUN) {
            runAO.listOpenRunsOfTestEngine(engine.getId()).forEach(run -> workflowExecutionBO.stopTransactional(run.getId()));
        }
    }

    @Transactional
    public void finishSingleRunEnginesTransactional(List<Long> testRunIds) {
        runAO.listByIds(testRunIds).stream()
                .filter(ExecutionRunEntity::hasEnded)
                .map(ExecutionRunEntity::getTestEngine)
                .filter(engine -> engine != null && engine.getEngineMode() == TestEngineMode.SINGLE_RUN && !engine.hasEnded())
                .map(TestEngineRunBO::attemptOf)
                .flatMap(Optional::stream)
                .forEach(attempt -> end(attempt, RunStatusTypes.FINISHED, null, null));
    }

    @Transactional
    String findContainerIdTransactional(Long attemptId) {
        return attemptAO.findByIdOptional(attemptId).map(ExecutionAttemptEntity::getContainerId).orElse(null);
    }

    @Transactional
    String findContainerIdTransactional(Long appId, Long runId, String keycloakId) {
        testRunBO.requireAuthor(appId, keycloakId);
        return attemptOf(runAO.findEngineOfApp(runId, appId).orElseThrow(NotFoundException::new))
                .map(ExecutionAttemptEntity::getContainerId).orElse(null);
    }

    @Transactional
    public boolean hasConnectingEngineTransactional(Long appId) {
        return runAO.findConnectingEngineOfApp(appId).isPresent();
    }

    @Transactional
    public void requireAuthorTransactional(Long appId, String keycloakId) {
        testRunBO.requireAuthor(appId, keycloakId);
    }

    // A step selects its attempt when it ends; until then the engine runs its latest one
    static Optional<ExecutionAttemptEntity> attemptOf(ExecutionRunEntity engine) {
        return engine.getCurrentStep().flatMap(step -> Optional.ofNullable(step.getSelectedAttempt())
                .or(() -> step.getAttempts().stream().max(Comparator.comparing(ExecutionAttemptEntity::getId))));
    }

    private Optional<Long> connectingAttemptIdOf(Long appId) {
        return runAO.findConnectingEngineOfApp(appId).flatMap(TestEngineRunBO::attemptOf).map(ExecutionAttemptEntity::getId);
    }

    private void end(ExecutionAttemptEntity attempt, RunStatusTypes status, ExecutionFailure failure, Integer exitCode) {
        if (attempt.hasEnded()) {
            return;
        }
        attempt.setExitCode(exitCode);
        attempt.end(status, failure);
        toolApiKeyService.revokeExecution(attempt.getId());
        events.attemptFinished(attempt);
    }
}
