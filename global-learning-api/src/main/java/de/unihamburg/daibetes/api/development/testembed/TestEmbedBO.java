package de.unihamburg.daibetes.api.development.testembed;

import bio.cosy.feddb.core.api.app.FederatedAppDetailDTO;
import bio.cosy.feddb.core.api.app.FederatedAppVersionDTO;
import bio.cosy.feddb.core.execution.message.ExecutionMessageLogDTO;
import bio.cosy.feddb.core.execution.message.ExecutionMessageMetricDTO;
import bio.cosy.feddb.core.execution.message.ExecutionMessageRoundDTO;
import bio.cosy.feddb.core.execution.run.dto.AppMessageWrapperDTO;
import bio.cosy.feddb.core.execution.run.types.AppMessageTypeEnum;
import bio.cosy.feddb.core.execution.run.types.AppRunTypeEnum;
import de.unihamburg.daibetes.api.app.FederatedAppBO;
import de.unihamburg.daibetes.api.app.version.FederatedAppVersionBO;
import de.unihamburg.daibetes.api.development.run.TestRunBO;
import de.unihamburg.daibetes.api.development.run.federated.FederatedRoundMessageCreateDTO;
import de.unihamburg.daibetes.api.development.testembed.pydantic.PydanticClassGenerator;
import de.unihamburg.daibetes.api.development.testembed.pydantic.PydanticUpdateDTO;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.Date;
import java.util.Optional;

@ApplicationScoped
public class TestEmbedBO {

    @Inject
    TestRunBO testRunBO;

    @Inject
    FederatedAppBO federatedAppBO;

    @Inject
    FederatedAppVersionBO federatedAppVersionBO;

    public AppMessageWrapperDTO<PydanticUpdateDTO> pydanticObjOf(FederatedAppDetailDTO config) {
        return new AppMessageWrapperDTO<>(AppMessageTypeEnum.CONFIG_PYDANTIC_CHANGED,
                PydanticClassGenerator.generatePydanticClasses(config));
    }

    // Forwarded as it comes, the run sent to the frontend leaves round messages out.
    public AppMessageWrapperDTO<ExecutionMessageRoundDTO> logFederatedRoundMessage(FederatedRoundMessageCreateDTO create) {
        ExecutionMessageRoundDTO dto = testRunBO.storeRoundMessageTransactional(create);
        return new AppMessageWrapperDTO<>(AppMessageTypeEnum.FEDERATED_ROUND_MESSAGE, dto, AppRunTypeEnum.FEDERATED_TEST_RUN);
    }

    @Transactional
    public AppMessageWrapperDTO<FederatedAppDetailDTO> saveAppConfigTransactional(FederatedAppDetailDTO m, Long appId, boolean isTool) {
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
    public Optional<AppMessageWrapperDTO<FederatedAppDetailDTO>> findAppConfigTransactional(Long appId) {
        try {
            FederatedAppDetailDTO found = federatedAppBO.getMyAppById(appId);
            return Optional.of(new AppMessageWrapperDTO<>(AppMessageTypeEnum.CONFIG_INITIAL_SEND, found));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    // Forwarded as stored, with the attempt it belongs to as its run.
    @Transactional
    public AppMessageWrapperDTO<ExecutionMessageLogDTO> logTestMessageTransactional(Long runId, ExecutionMessageLogDTO log, AppRunTypeEnum runType) {
        ExecutionMessageLogDTO dto = testRunBO.storeLogTransactional(runId, log, runType);
        return new AppMessageWrapperDTO<>(AppMessageTypeEnum.LOG_MESSAGE, dto, forwardedRunType(runType));
    }

    @Transactional
    public AppMessageWrapperDTO<ExecutionMessageMetricDTO> logTestMetricTransactional(Long runId, ExecutionMessageMetricDTO log, AppRunTypeEnum runType) {
        ExecutionMessageMetricDTO dto = testRunBO.storeMetricTransactional(runId, log, runType);
        return new AppMessageWrapperDTO<>(AppMessageTypeEnum.LOG_METRIC, dto, forwardedRunType(runType));
    }

    private AppRunTypeEnum forwardedRunType(AppRunTypeEnum runType) {
        if (runType != null && runType.isFederated()) {
            return AppRunTypeEnum.FEDERATED_TEST_RUN;
        }
        return runType == AppRunTypeEnum.EXPERIMENT_RUN ? AppRunTypeEnum.EXPERIMENT_RUN : AppRunTypeEnum.TEST_RUN;
    }

}
