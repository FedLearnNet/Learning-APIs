package de.unihamburg.daibetes.execution.attempt;

import bio.cosy.feddb.core.execution.attempt.BaseAttemptLifecycle;
import bio.cosy.feddb.core.execution.ExecutionPurpose;
import bio.cosy.feddb.core.execution.run.dto.StartRunDTO;
import de.unihamburg.daibetes.api.analysis.run.DataAnalysisRunResultBO;
import de.unihamburg.daibetes.api.project.training.federated.FederatedTrainingAggregatorBO;
import de.unihamburg.daibetes.api.project.training.platform.PlatformTrainingBO;
import de.unihamburg.daibetes.execution.message.ExecutionMessageAO;
import de.unihamburg.daibetes.execution.message.ExecutionMessageEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import de.unihamburg.daibetes.api.development.run.federated.HostedFederatedTestBO;

@ApplicationScoped
public class ExecutionAttemptLifecycle extends BaseAttemptLifecycle<ExecutionAttemptEntity, ExecutionAttemptAO,
        ExecutionMessageEntity, ExecutionMessageAO> {

    @Inject
    DataAnalysisRunResultBO analysisResultBO;

    @Inject
    PlatformTrainingBO platformTrainingBO;

    @Inject
    FederatedTrainingAggregatorBO aggregatorBO;

    @Inject HostedFederatedTestBO hostedTests;

    @Override
    protected String ownerOf(ExecutionAttemptEntity attempt) {
        return attempt.getStep().getRun().getKeycloakId();
    }

    public ExecutionAttemptLifecycle() {
        super(attempt -> {
            ExecutionMessageEntity message = new ExecutionMessageEntity();
            message.setAttempt(attempt);
            return message;
        });
    }

    @Transactional
    public boolean isTestTransactional(Long attemptId) {
        return attemptAO.findByIdOptional(attemptId)
                .map(attempt -> attempt.getStep().getRun().getPurpose() == ExecutionPurpose.TEST).orElse(false);
    }

    @Transactional
    public boolean predictsTransactional(Long attemptId) {
        return attemptAO.findByIdOptional(attemptId)
                .map(attempt -> attempt.getStep().getSubModel() != null)
                .orElse(false);
    }

    @Override
    protected void customizeStartup(ExecutionAttemptEntity attempt, StartRunDTO start) {
        super.customizeStartup(attempt, start);
        switch (attempt.getStep().getRun().getPurpose()) {
            case TEST -> {
                if (HostedFederatedTestBO.isHosted(attempt)) hostedTests.customizeStartup(attempt, start);
            }
            case ANALYSIS -> {
                analysisResultBO.withInputFileUrlsTransactional(start);
                platformTrainingBO.inputFilePathsOf(attempt).forEach((name, file) -> {
                    if (!start.getInputData().containsKey(name)) {
                        start.getInputFilePaths().putIfAbsent(name, file);
                    }
                });
            }
            case TRAINING -> {
                if (FederatedTrainingAggregatorBO.isAggregator(attempt)) {
                    aggregatorBO.customizeStartup(attempt, start);
                } else {
                    start.setInputFilePaths(platformTrainingBO.inputFilePathsOf(attempt));
                }
            }
            default -> {
            }
        }
    }
}
