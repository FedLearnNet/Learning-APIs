package de.unihamburg.daibetes.api.testembed;

import bio.cosy.feddb.core.api.app.FederatedAppDetailDTO;
import bio.cosy.feddb.core.api.app.FederatedAppVersionDTO;
import bio.cosy.feddb.core.api.model.ModelDTO;
import bio.cosy.feddb.core.api.model.ModelSubDataDTO;
import bio.cosy.feddb.core.api.run.*;
import bio.cosy.feddb.core.api.run.message.RunMessageLogDTO;
import bio.cosy.feddb.core.api.run.message.RunMessageMetricDTO;
import bio.cosy.feddb.core.helper.FileHelper;
import de.unihamburg.daibetes.api.app.FederatedAppBO;
import de.unihamburg.daibetes.api.app.version.FederatedAppVersionBO;
import de.unihamburg.daibetes.api.model.ModelBO;
import de.unihamburg.daibetes.api.runs.experiment.ExperimentBO;
import de.unihamburg.daibetes.api.runs.experiment.ExperimentDTO;
import de.unihamburg.daibetes.api.runs.experiment.message.ExperimentRunMessageBO;
import de.unihamburg.daibetes.api.runs.experiment.run.ExperimentRunBO;
import de.unihamburg.daibetes.api.runs.experiment.run.ExperimentRunDTO;
import de.unihamburg.daibetes.api.runs.experiment.run.UpdateExperimentRunDTO;
import de.unihamburg.daibetes.api.runs.test.TestRunBO;
import de.unihamburg.daibetes.api.runs.test.TestRunCreateDTO;
import de.unihamburg.daibetes.api.runs.test.TestRunDTO;
import de.unihamburg.daibetes.api.runs.test.federated.FederatedTestRunBO;
import de.unihamburg.daibetes.api.runs.test.federated.FederatedTestRunCreateDTO;
import de.unihamburg.daibetes.api.runs.test.federated.FederatedTestRunDTO;
import de.unihamburg.daibetes.api.runs.test.federated.message.FederatedParticipantMessageBO;
import de.unihamburg.daibetes.api.runs.test.federated.message.FederatedRoundMessageBO;
import de.unihamburg.daibetes.api.runs.test.federated.message.FederatedRoundMessageCreateDTO;
import de.unihamburg.daibetes.api.runs.test.federated.message.FederatedRoundMessageDTO;
import de.unihamburg.daibetes.api.runs.test.federated.participant.FederatedParticipantBO;
import de.unihamburg.daibetes.api.runs.test.federated.participant.FederatedParticipantDTO;
import de.unihamburg.daibetes.api.runs.test.federated.participant.FederatedParticipantUpdateDTO;
import de.unihamburg.daibetes.api.runs.test.message.TestRunMessageBO;
import de.unihamburg.daibetes.api.testembed.pydantic.PydanticClassGenerator;
import de.unihamburg.daibetes.api.testembed.pydantic.PydanticUpdateDTO;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;

@Slf4j
@ApplicationScoped
public class TestEmbedBO {

    @Inject
    TestRunBO testRunBO;

    @Inject
    FederatedTestRunBO federatedTestRunBO;

    @Inject
    FederatedParticipantMessageBO federatedParticipantMessageBO;

    @Inject
    FederatedParticipantBO federatedParticipantBO;

    @Inject
    FederatedRoundMessageBO federatedRoundMessageBO;

    @Inject
    ExperimentRunBO experimentRunBO;

    @Inject
    ExperimentBO experimentBO;

    @Inject
    FederatedAppBO federatedAppBO;

    @Inject
    TestRunMessageBO testRunMessageBO;

    @Inject
    ExperimentRunMessageBO experimentRunMessageBO;

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

    @Transactional
    public <T> AppMessageWrapperDTO<StartRunDTO> startTest(Long appId, TestRunCreateDTO createDTO, String keycloakId) {
        StartRunDTO testDto = testRunBO.startTest(appId, createDTO);
        return new AppMessageWrapperDTO<>(AppMessageTypeEnum.START_RUN, testDto, AppRunTypeEnum.TEST_RUN);
    }

    @Transactional
    public AppMessageWrapperDTO<?> updateRun(UpdateRunDTO updateTest, AppRunTypeEnum runType) {
        if (runType == AppRunTypeEnum.TEST_RUN) {
            TestRunDTO testDto = testRunBO.updateTest(updateTest);
            return new AppMessageWrapperDTO<>(AppMessageTypeEnum.UPDATE_RUN, testDto);
        } else if (runType == AppRunTypeEnum.EXPERIMENT_RUN) {
            ExperimentRunDTO testDto = experimentRunBO.updateTest(updateTest);
            return new AppMessageWrapperDTO<>(AppMessageTypeEnum.UPDATE_RUN, testDto);
        } else if (isFederatedTestRun(runType)) {
            FederatedTestRunDTO update = new FederatedTestRunDTO();
            update.setId(updateTest.getRunId());
            update.setStatus(updateTest.getStatus());
            update.setError(updateTest.getError());
            FederatedTestRunDTO testDto = federatedTestRunBO.updateRun(update);
            return new AppMessageWrapperDTO<>(AppMessageTypeEnum.UPDATE_RUN, testDto, AppRunTypeEnum.FEDERATED_TEST_RUN);
        } else {
            throw new IllegalArgumentException("[updateRun] Unsupported run type: " + runType);
        }
    }

    public AppMessageWrapperDTO<?> uploadData(Long appId, OutputRunDataDTO finishTest, AppRunTypeEnum runType, String keycloakId) {
        if (runType == AppRunTypeEnum.TEST_RUN) {
            testRunBO.findByAppId(appId, keycloakId);
            TestRunDTO testDto = testRunBO.uploadData(finishTest);
            return new AppMessageWrapperDTO<>(AppMessageTypeEnum.FINISH_RUN, testDto);
        } else if (runType == AppRunTypeEnum.EXPERIMENT_RUN) {
            experimentRunBO.getById(appId, keycloakId);
            ExperimentRunDTO testDto = experimentRunBO.uploadData(finishTest);
            return new AppMessageWrapperDTO<>(AppMessageTypeEnum.FINISH_RUN, testDto);
        } else if (isFederatedTestRun(runType)) {
            federatedTestRunBO.findByAppId(appId, keycloakId);
            FederatedTestRunDTO testDto = federatedTestRunBO.uploadData(finishTest);
            return new AppMessageWrapperDTO<>(AppMessageTypeEnum.FINISH_RUN, testDto, AppRunTypeEnum.FEDERATED_TEST_RUN);
        } else {
            throw new IllegalArgumentException("[finishRun] Unsupported run type: " + runType);
        }

    }

    public ByteArrayOutputStream downloadOutput(Long appId, Long runId, AppRunTypeEnum runType, String keycloakId) {
        LinkedHashMap<String, Object> map;
        if (runType == AppRunTypeEnum.TEST_RUN) {
            testRunBO.findByAppId(appId, keycloakId);
            TestRunDTO testDto = testRunBO.getById(runId);
            map = testDto.getOutputData();
        } else if (runType == AppRunTypeEnum.EXPERIMENT_RUN) {
            experimentRunBO.getById(appId, keycloakId);
            ExperimentRunDTO testDto = experimentRunBO.getById(runId);
            map = testDto.getOutputData();
        } else if (isFederatedTestRun(runType)) {
            FederatedTestRunDTO testDto = federatedTestRunBO.getByIdAuth(appId, runId, keycloakId);
            map = testDto.getOutputData();
        } else {
            throw new IllegalArgumentException("[download] Unsupported run type: " + runType);
        }
        try {
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            FileHelper.createOutputZip(byteArrayOutputStream, map);
            return byteArrayOutputStream;
        } catch (IOException e) {
            throw new RuntimeException("Error converting LinkedHashMap to JSON string", e);
        }
    }

    @Transactional
    public AppMessageWrapperDTO<?> finishRun(FinishRunDTO finishTest, AppRunTypeEnum runType) {
        if (runType == AppRunTypeEnum.TEST_RUN) {
            TestRunDTO testDto = testRunBO.finishTest(finishTest);
            return new AppMessageWrapperDTO<>(AppMessageTypeEnum.FINISH_RUN, testDto);
        } else if (runType == AppRunTypeEnum.EXPERIMENT_RUN) {
            ExperimentRunDTO testDto = experimentRunBO.finishTest(finishTest);
            return new AppMessageWrapperDTO<>(AppMessageTypeEnum.FINISH_RUN, testDto);
        } else if (isFederatedTestRun(runType)) {
            FederatedTestRunDTO update = new FederatedTestRunDTO();
            update.setId(finishTest.getRunId());
            update.setStatus(RunStatusTypes.FINISHED);
            FederatedTestRunDTO testDto = federatedTestRunBO.updateRun(update);
            return new AppMessageWrapperDTO<>(AppMessageTypeEnum.FINISH_RUN, testDto, AppRunTypeEnum.FEDERATED_TEST_RUN);
        } else {
            throw new IllegalArgumentException("[finishRun] Unsupported run type: " + runType);
        }

    }

    @Transactional
    public AppMessageWrapperDTO<FederatedTestRunDTO> startFederatedTest(Long appId, FederatedTestRunCreateDTO createDTO) {
        FederatedTestRunDTO dto = federatedTestRunBO.startFederatedTest(appId, createDTO);
        return new AppMessageWrapperDTO<>(AppMessageTypeEnum.START_FEDERATED_RUN, dto, AppRunTypeEnum.FEDERATED_TEST_RUN);
    }

    @Transactional
    public AppMessageWrapperDTO<FederatedTestRunDTO> updateFederatedRun(FederatedTestRunDTO update) {
        FederatedTestRunDTO dto = federatedTestRunBO.updateRun(update);
        return new AppMessageWrapperDTO<>(AppMessageTypeEnum.UPDATE_FEDERATED_RUN, dto, AppRunTypeEnum.FEDERATED_TEST_RUN);
    }

    @Transactional
    public AppMessageWrapperDTO<FederatedParticipantDTO> updateFederatedParticipant(FederatedParticipantUpdateDTO update) {
        FederatedParticipantDTO dto = federatedParticipantBO.updateParticipant(update);
        return new AppMessageWrapperDTO<>(AppMessageTypeEnum.FEDERATED_PARTICIPANT_UPDATE, dto, AppRunTypeEnum.FEDERATED_TEST_RUN);
    }

    @Transactional
    public AppMessageWrapperDTO<FederatedRoundMessageDTO> logFederatedRoundMessage(FederatedRoundMessageCreateDTO create) {
        FederatedRoundMessageDTO dto = federatedRoundMessageBO.create(create);
        return new AppMessageWrapperDTO<>(AppMessageTypeEnum.FEDERATED_ROUND_MESSAGE, dto, AppRunTypeEnum.FEDERATED_TEST_RUN);
    }

    @Transactional
    public List<AppMessageWrapperDTO<FederatedTestRunDTO>> closeAllFederatedOnDisconnect(Long appId) {
        return federatedTestRunBO.closeAllRunningOnDisconnect(appId).stream()
                .map(t -> new AppMessageWrapperDTO<>(AppMessageTypeEnum.UPDATE_FEDERATED_RUN, t, AppRunTypeEnum.FEDERATED_TEST_RUN))
                .toList();
    }

    @Transactional
    public List<AppMessageWrapperDTO<TestRunDTO>> closeAllTest(Long appId) {
        List<TestRunDTO> testDto = testRunBO.closeAllTestCauseOfDisconnecting(appId);
        return testDto.stream().map(t -> new AppMessageWrapperDTO<>(AppMessageTypeEnum.UPDATE_RUN, t)).toList();
    }

    @Transactional
    public Optional<AppMessageWrapperDTO<UpdateExperimentRunDTO>> getNextExperimentRun(Long appId) {

        return experimentRunBO.findNextByExperimentId(appId)
                .map(
                        run -> new AppMessageWrapperDTO<>(AppMessageTypeEnum.START_RUN,
                                run,
                                AppRunTypeEnum.EXPERIMENT_RUN));
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

    @Transactional
    public AppMessageWrapperDTO<RunMessageLogDTO> logTestMessage(Long runId, RunMessageLogDTO log, AppRunTypeEnum runType) {
        if (runType == AppRunTypeEnum.EXPERIMENT_RUN) {
            RunMessageLogDTO dto = experimentRunMessageBO.create(log, runId);
            return new AppMessageWrapperDTO<>(AppMessageTypeEnum.LOG_MESSAGE, dto);
        }
        if (isFederatedTestRun(runType)) {
            String participantId = log.getWorkerId();
            RunMessageLogDTO dto = federatedParticipantMessageBO.create(log, runId, participantId);
            return new AppMessageWrapperDTO<>(AppMessageTypeEnum.LOG_MESSAGE, dto, AppRunTypeEnum.FEDERATED_TEST_RUN);
        }
        RunMessageLogDTO dto = testRunMessageBO.create(log, runId);
        return new AppMessageWrapperDTO<>(AppMessageTypeEnum.LOG_MESSAGE, dto);
    }

    @Transactional
    public AppMessageWrapperDTO<RunMessageMetricDTO> logTestMetric(Long runId, RunMessageMetricDTO log, AppRunTypeEnum runType) {
        if (runType == AppRunTypeEnum.TEST_RUN) {
            RunMessageMetricDTO dto = testRunMessageBO.create(log, runId);
            return new AppMessageWrapperDTO<>(AppMessageTypeEnum.LOG_METRIC, dto);
        } else if (runType == AppRunTypeEnum.EXPERIMENT_RUN) {
            RunMessageMetricDTO dto = experimentRunMessageBO.create(log, runId);
            return new AppMessageWrapperDTO<>(AppMessageTypeEnum.LOG_METRIC, dto);
        } else if (isFederatedTestRun(runType)) {
            String participantId = log.getWorkerId();
            RunMessageMetricDTO dto = federatedParticipantMessageBO.create(log, runId, participantId);
            return new AppMessageWrapperDTO<>(AppMessageTypeEnum.LOG_METRIC, dto, AppRunTypeEnum.FEDERATED_TEST_RUN);
        } else {
            throw new IllegalArgumentException("[logTestMetric] Unsupported run type: " + runType);
        }
    }

    public AppMessageWrapperDTO<ModelDTO> saveModel(ModelSubDataDTO model, String keycloakId) {
        ExperimentRunDTO runDto = experimentRunBO.getById(model.getRunId(), keycloakId);
        ExperimentDTO experiment = experimentBO.getById(runDto.getExperimentId());

        ModelDTO dto = modelBo.saveModel(experiment.getFederatedAppVersionId(),
                runDto.getExperimentId(),
                experiment.getFederatedAppId(),
                model, keycloakId);
        return new AppMessageWrapperDTO<>(AppMessageTypeEnum.SEND_MODEL, dto);

    }

}
