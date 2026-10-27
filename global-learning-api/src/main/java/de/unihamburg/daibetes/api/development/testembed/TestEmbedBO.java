package de.unihamburg.daibetes.api.development.testembed;

import de.unihamburg.daibetes.execution.run.ExecutionRunDetailDTO;

import bio.cosy.feddb.core.api.app.FederatedAppDetailDTO;
import bio.cosy.feddb.core.api.app.FederatedAppVersionDTO;
import bio.cosy.feddb.core.api.model.ModelDTO;
import bio.cosy.feddb.core.api.model.ModelSubDataDTO;
import bio.cosy.feddb.core.execution.message.ExecutionMessageLogDTO;
import bio.cosy.feddb.core.execution.message.ExecutionMessageMetricDTO;
import bio.cosy.feddb.core.execution.message.ExecutionMessageRoundDTO;
import bio.cosy.feddb.core.execution.run.dto.*;
import bio.cosy.feddb.core.execution.run.types.AppMessageTypeEnum;
import bio.cosy.feddb.core.execution.run.types.AppRunTypeEnum;
import bio.cosy.feddb.core.helper.FileHelper;
import de.unihamburg.daibetes.api.app.FederatedAppBO;
import de.unihamburg.daibetes.api.app.version.FederatedAppVersionBO;
import de.unihamburg.daibetes.api.model.ModelBO;
import de.unihamburg.daibetes.api.development.run.TestRunBO;
import de.unihamburg.daibetes.api.development.run.TestRunCreateDTO;
import de.unihamburg.daibetes.api.development.run.TestRunLauncher;
import de.unihamburg.daibetes.api.development.run.federated.FederatedParticipantUpdateDTO;
import de.unihamburg.daibetes.api.development.run.federated.FederatedRoundMessageCreateDTO;
import de.unihamburg.daibetes.api.development.run.federated.FederatedTestRunCreateDTO;
import de.unihamburg.daibetes.api.development.testembed.pydantic.PydanticClassGenerator;
import de.unihamburg.daibetes.api.development.testembed.pydantic.PydanticUpdateDTO;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Optional;

@Slf4j
@ApplicationScoped
public class TestEmbedBO {

    @Inject
    TestRunBO testRunBO;

    @Inject
    TestRunLauncher testRunLauncher;

    @Inject
    FederatedAppBO federatedAppBO;

    @Inject
    FederatedAppVersionBO federatedAppVersionBO;

    @Inject
    ModelBO modelBo;

    private boolean isFederatedTestRun(AppRunTypeEnum runType) {
        return runType == AppRunTypeEnum.FEDERATED_RUN || runType == AppRunTypeEnum.FEDERATED_TEST_RUN;
    }

    public AppMessageWrapperDTO<PydanticUpdateDTO> getPydanticObj(FederatedAppDetailDTO config) {
        AppMessageWrapperDTO<PydanticUpdateDTO> dto = new AppMessageWrapperDTO<>(AppMessageTypeEnum.CONFIG_PYDANTIC_CHANGED,
                PydanticClassGenerator.generatePydanticClasses(config));
        return dto;

    }

    // The app engine gets the run once it is launched; the frontend follows it through the test run sender.
    public void startTest(Long appId, TestRunCreateDTO createDTO, String keycloakId) {
        testRunBO.startTest(appId, createDTO, keycloakId);
    }

    // A test run and an experiment trial are addressed by their attempt, a federated test run by its run.
    @Transactional
    public void updateRun(UpdateRunDTO updateTest, AppRunTypeEnum runType) {
        if (isFederatedTestRun(runType)) {
            FederatedRunLifecycleDTO update = new FederatedRunLifecycleDTO();
            update.setId(updateTest.getRunId());
            update.setStatus(updateTest.getStatus() != null ? updateTest.getStatus().name() : null);
            update.setError(updateTest.getError());
            testRunBO.updateFederatedRun(update);
        } else {
            testRunBO.update(updateTest);
        }
    }

    public AppMessageWrapperDTO<?> uploadData(Long appId, OutputRunDataDTO finishTest, AppRunTypeEnum runType, String keycloakId) {
        ExecutionRunDetailDTO testDto = testRunBO.uploadOutput(finishTest, runType);
        return new AppMessageWrapperDTO<>(AppMessageTypeEnum.FINISH_RUN, testDto, runType);
    }

    // The frontend downloads a test run by its run, a trial of an experiment by its attempt.
    public ByteArrayOutputStream downloadOutput(Long appId, Long runId, AppRunTypeEnum runType, String keycloakId) {
        LinkedHashMap<String, Object> map = runType == AppRunTypeEnum.EXPERIMENT_RUN
                ? testRunBO.outputOfAttempt(appId, runId, keycloakId)
                : testRunBO.outputOf(appId, runId, keycloakId);
        try {
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            FileHelper.createOutputZip(byteArrayOutputStream, map);
            return byteArrayOutputStream;
        } catch (IOException e) {
            throw new RuntimeException("Error converting LinkedHashMap to JSON string", e);
        }
    }

    @Transactional
    public void finishRun(FinishRunDTO finishTest, AppRunTypeEnum runType) {
        if (isFederatedTestRun(runType)) {
            testRunBO.finishFederated(finishTest);
        } else {
            testRunBO.finish(finishTest);
        }
    }

    public void startFederatedTest(Long appId, FederatedTestRunCreateDTO createDTO, String keycloakId) {
        testRunBO.startFederatedTest(appId, createDTO, keycloakId);
    }

    public void updateFederatedRun(FederatedRunLifecycleDTO update) {
        testRunBO.updateFederatedRun(update);
    }

    public void updateFederatedParticipant(FederatedParticipantUpdateDTO update) {
        testRunBO.updateParticipant(update);
    }

    // Forwarded as it comes, the run sent to the frontend leaves round messages out.
    public AppMessageWrapperDTO<ExecutionMessageRoundDTO> logFederatedRoundMessage(FederatedRoundMessageCreateDTO create) {
        ExecutionMessageRoundDTO dto = testRunBO.roundMessage(create);
        return new AppMessageWrapperDTO<>(AppMessageTypeEnum.FEDERATED_ROUND_MESSAGE, dto, AppRunTypeEnum.FEDERATED_TEST_RUN);
    }

    public void closeAllTest(Long appId) {
        testRunBO.closeAllOnDisconnect(appId);
    }

    // Test runs and experiment trials queued while no app engine was connected.
    public void launchPendingTestRuns(Long appId) {
        testRunLauncher.launchPending(appId);
    }

    @Transactional
    public AppMessageWrapperDTO<FederatedAppDetailDTO> saveAppConfig(FederatedAppDetailDTO m, Long appId, boolean isTool) {
        FederatedAppDetailDTO found = federatedAppBO.getById(appId);
        if (found == null) {
            Log.errorf("Try to update App with id %d via WS but not found", appId);
            return new AppMessageWrapperDTO<>(AppMessageTypeEnum.DO_NOTHING, null);
        }
        boolean isToolYmlEqual = found.toolYmlIsEqualTo(m);
        if (isTool && isToolYmlEqual) {
            Log.infof("No changes in tool.yml for app id %d, skipping update", appId);
            return new AppMessageWrapperDTO<>(AppMessageTypeEnum.DO_NOTHING, found);
        }
        FederatedAppVersionDTO latestVersion = federatedAppVersionBO.getById(found.getLatestUnpublishedVersionId());
        m.setId(appId);
        m.setCreatedAt(found.getCreatedAt());
        m.setUpdatedAt(new Date());
        m.setVersion(found.getVersion());
        m.setLatestVersionId(found.getLatestUnpublishedVersionId());
        m.setLatestVersion(latestVersion.getAppVersion());
        m.setImageName(found.getImageName());
        m.setSupportsFederatedLearning(found.getSupportsFederatedLearning());
        FederatedAppDetailDTO dto = federatedAppBO.update(m);
        if (!isTool && isToolYmlEqual) {
            Log.infof("No changes in tool.yml for app id %d, skipping update", appId);
            return new AppMessageWrapperDTO<>(AppMessageTypeEnum.DO_NOTHING, found);
        }
        return new AppMessageWrapperDTO<>(AppMessageTypeEnum.CONFIG_CHANGED, dto);
    }

    @Transactional
    public Optional<AppMessageWrapperDTO<FederatedAppDetailDTO>> getAppConfig(Long appId) {
        try {
            FederatedAppDetailDTO found = federatedAppBO.getMyAppById(appId);
            return Optional.of(new AppMessageWrapperDTO<>(AppMessageTypeEnum.CONFIG_INITIAL_SEND, found));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    // Forwarded as stored, with the attempt it belongs to as its run.
    @Transactional
    public AppMessageWrapperDTO<ExecutionMessageLogDTO> logTestMessage(Long runId, ExecutionMessageLogDTO log, AppRunTypeEnum runType) {
        ExecutionMessageLogDTO dto = testRunBO.log(runId, log, runType);
        return new AppMessageWrapperDTO<>(AppMessageTypeEnum.LOG_MESSAGE, dto, forwardedRunType(runType));
    }

    @Transactional
    public AppMessageWrapperDTO<ExecutionMessageMetricDTO> logTestMetric(Long runId, ExecutionMessageMetricDTO log, AppRunTypeEnum runType) {
        ExecutionMessageMetricDTO dto = testRunBO.metric(runId, log, runType);
        return new AppMessageWrapperDTO<>(AppMessageTypeEnum.LOG_METRIC, dto, forwardedRunType(runType));
    }

    // The trial of an experiment that trained the model links it to the experiment.
    public AppMessageWrapperDTO<ModelDTO> saveModel(ModelSubDataDTO model, String keycloakId) {
        ExecutionRunDetailDTO experiment = testRunBO.runOfAttempt(model.getRunId(), keycloakId);
        ModelDTO dto = modelBo.saveModel(experiment.getFederatedAppVersionId(),
                experiment.getExperimentId(),
                experiment.getFederatedAppId(),
                model, keycloakId);
        return new AppMessageWrapperDTO<>(AppMessageTypeEnum.SEND_MODEL, dto);

    }

    private AppRunTypeEnum forwardedRunType(AppRunTypeEnum runType) {
        if (isFederatedTestRun(runType)) {
            return AppRunTypeEnum.FEDERATED_TEST_RUN;
        }
        return runType == AppRunTypeEnum.EXPERIMENT_RUN ? AppRunTypeEnum.EXPERIMENT_RUN : AppRunTypeEnum.TEST_RUN;
    }

}
