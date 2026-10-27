package de.unihamburg.daibetes.execution.attempt;

import bio.cosy.feddb.core.execution.attempt.BaseAttemptLifecycle;
import bio.cosy.feddb.core.execution.run.dto.StartRunDTO;
import de.unihamburg.daibetes.api.analysis.run.DataAnalysisRunResultBO;
import de.unihamburg.daibetes.api.project.training.platform.PlatformTrainingBO;
import de.unihamburg.daibetes.execution.message.ExecutionMessageAO;
import de.unihamburg.daibetes.execution.message.ExecutionMessageEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class ExecutionAttemptLifecycle extends BaseAttemptLifecycle<ExecutionAttemptEntity, ExecutionAttemptAO,
        ExecutionMessageEntity, ExecutionMessageAO> {

    @Inject
    DataAnalysisRunResultBO analysisResultBO;

    @Inject
    PlatformTrainingBO platformTrainingBO;

    public ExecutionAttemptLifecycle() {
        super(attempt -> {
            ExecutionMessageEntity message = new ExecutionMessageEntity();
            message.setAttempt(attempt);
            return message;
        });
    }

    @Override
    protected void customizeStartup(ExecutionAttemptEntity attempt, StartRunDTO start) {
        super.customizeStartup(attempt, start);
        switch (attempt.getStep().getRun().getPurpose()) {
            case ANALYSIS -> analysisResultBO.withInputFileUrlsTransactional(start);
            case TRAINING -> start.setInputFilePaths(platformTrainingBO.inputFilePathsOf(attempt));
            default -> {
            }
        }
    }
}
