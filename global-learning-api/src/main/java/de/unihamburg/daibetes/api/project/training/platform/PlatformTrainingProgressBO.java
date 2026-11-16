package de.unihamburg.daibetes.api.project.training.platform;

import bio.cosy.feddb.core.execution.file.ExecutionFileDirection;
import bio.cosy.feddb.core.execution.orchestrator.WorkflowOrchestrator;
import de.unihamburg.daibetes.execution.attempt.ExecutionAttemptEntity;
import de.unihamburg.daibetes.execution.file.ExecutionFileBO;
import de.unihamburg.daibetes.execution.step.ExecutionRunStepAO;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.util.List;

@ApplicationScoped
public class PlatformTrainingProgressBO {

    @Inject
    ExecutionRunStepAO stepAO;

    @Inject
    WorkflowOrchestrator orchestrator;

    @Inject
    ExecutionFileBO executionFileBO;

    public void keepOutputs(Long stepId) {
        stepAO.findProjectTrainingStepOptional(stepId)
                .filter(step -> step.getSelectedAttempt() != null)
                .ifPresent(step -> {
                    ExecutionAttemptEntity attempt = step.getSelectedAttempt();
                    List<File> written = orchestrator.filesOf(step.getRun().getId(), attempt.getId());
                    try {
                        executionFileBO.storeAndLinkFiles(attempt, written, ExecutionFileDirection.OUTPUT);
                        Log.infof("Kept %d output(s) of attempt %d", written.size(), attempt.getId());
                    } finally {
                        written.forEach(FileUtils::deleteQuietly);
                    }
                });
    }
}
