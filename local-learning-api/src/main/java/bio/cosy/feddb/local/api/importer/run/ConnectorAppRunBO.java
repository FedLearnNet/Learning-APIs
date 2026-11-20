package bio.cosy.feddb.local.api.importer.run;

import bio.cosy.feddb.core.execution.run.dto.AppRunUploadData;
import bio.cosy.feddb.local.api.importer.files.table.TableData;
import bio.cosy.feddb.local.api.importer.transformer.ConnectorTransformerDTO;
import bio.cosy.feddb.local.execution.attempt.ExecutionAttemptEntity;
import bio.cosy.feddb.local.execution.run.ExecutionRunDTO;
import bio.cosy.feddb.core.execution.orchestrator.WorkflowOrchestrator;
import io.quarkus.logging.Log;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class ConnectorAppRunBO {

    private final ConcurrentHashMap<Long, LinkedHashMap<String, File>> stagedInputs = new ConcurrentHashMap<>();

    @Inject
    ConnectorAppRunSetupBO setupBO;

    @Inject
    ConnectorAppRunProgressBO progressBO;

    @Inject
    ConnectorAppRunCleanupBO cleanupBO;

    @Inject
    WorkflowOrchestrator orchestrator;

    @Transactional
    public ExecutionRunDTO startTransactional(ConnectorTransformerDTO app, LinkedHashMap<String, Object> hyperParams, Long connectorRunId,
                                              Long cohortId, String keycloakId, boolean session) {
        return startTransactional(app, hyperParams, null, null, null, connectorRunId, cohortId, keycloakId, session);
    }

    @Transactional
    public ExecutionRunDTO startTransactional(
            ConnectorTransformerDTO app,
            LinkedHashMap<String, Object> hyperParams,
            LinkedHashMap<String, Object> inputs,
            LinkedHashMap<String, String> inputFilePaths,
            LinkedHashMap<String, File> filesByVolumeName,
            Long connectorRunId,
            Long cohortId,
            String keycloakId,
            boolean session
    ) {
        ExecutionRunDTO run = setupBO.start(app, hyperParams, inputs, inputFilePaths, connectorRunId, cohortId, keycloakId);
        if (filesByVolumeName != null && !filesByVolumeName.isEmpty()) {
            stagedInputs.put(run.getId(), new LinkedHashMap<>(filesByVolumeName));
        }
        if (session) {
            progressBO.registerSession(run.getId());
        }
        return run;
    }

    /** Puts staged PATH inputs into the attempt volume before the container starts. */
    public void stageInputs(ExecutionAttemptEntity attempt) {
        Long runId = attempt.getStep().getRun().getId();
        LinkedHashMap<String, File> files = stagedInputs.remove(runId);
        if (files == null || files.isEmpty()) {
            return;
        }
        try {
            for (Map.Entry<String, File> entry : files.entrySet()) {
                File file = entry.getValue();
                if (file == null || !file.isFile()) {
                    throw new IllegalStateException(
                            "Staged extractor input '" + entry.getKey() + "' is missing for run " + runId);
                }
                orchestrator.uploadFilesToVolume(runId, attempt.getId(), file, entry.getKey());
                Log.infof("Staged extractor input '%s' (%s) into attempt %d",
                        entry.getKey(), FileUtils.byteCountToDisplaySize(file.length()), attempt.getId());
            }
        } finally {
            files.values().forEach(FileUtils::deleteQuietly);
        }
    }

    public Uni<ConnectorAppOutputDTO> awaitOutput(Long runId, Duration timeout) {
        return progressBO.awaitOutput(runId, timeout);
    }

    public void uploadOutput(Long attemptId, AppRunUploadData upload) {
        TableData table;
        try {
            table = progressBO.tableOf(upload);
        } catch (RuntimeException e) {
            failAttempt(attemptId, "Uploaded output could not be read: " + e.getMessage());
            throw e;
        }
        if (progressBO.completeBatch(attemptId, upload.getRunId(), table)) {
            return;
        }
        progressBO.storeOutputTransactional(attemptId, table, upload);
        cleanupBO.finishAttempt(attemptId);
    }

    public boolean isSession(Long runId) {
        return progressBO.isSession(runId);
    }

    public boolean isSessionAttempt(Long attemptId) {
        return progressBO.isSessionAttempt(attemptId);
    }

    public Long sessionRunIdOf(Long attemptId) {
        return progressBO.sessionRunIdOf(attemptId);
    }

    public Uni<Long> connectedAttemptId(Long runId) {
        return progressBO.connectedAttemptId(runId);
    }

    public CompletableFuture<TableData> expectBatch(Long attemptId, Long batchId) {
        return progressBO.expectBatch(attemptId, batchId);
    }

    public void forgetBatch(Long batchId) {
        progressBO.forgetBatch(batchId);
    }

    public void endSession(Long attemptId) {
        progressBO.forgetSessionAttempt(attemptId);
        cleanupBO.finishAttempt(attemptId);
    }

    public void failAttempt(Long attemptId, String error) {
        progressBO.forgetSessionAttempt(attemptId);
        cleanupBO.failAttempt(attemptId, error);
    }

    public void forgetSession(Long runId) {
        progressBO.forgetSession(runId);
        LinkedHashMap<String, File> leftover = stagedInputs.remove(runId);
        if (leftover != null) {
            leftover.values().forEach(FileUtils::deleteQuietly);
        }
    }
}
