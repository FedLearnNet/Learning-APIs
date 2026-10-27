package bio.cosy.feddb.local.api.importer.run;

import bio.cosy.feddb.core.execution.run.dto.AppRunUploadData;
import bio.cosy.feddb.local.api.importer.files.table.TableData;
import bio.cosy.feddb.local.api.importer.transformer.ConnectorTransformerDTO;
import bio.cosy.feddb.local.execution.run.ExecutionRunDTO;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.concurrent.CompletableFuture;

@ApplicationScoped
public class ConnectorAppRunBO {

    @Inject
    ConnectorAppRunSetupBO setupBO;

    @Inject
    ConnectorAppRunProgressBO progressBO;

    @Inject
    ConnectorAppRunCleanupBO cleanupBO;
    @Transactional
    public ExecutionRunDTO startTransactional(ConnectorTransformerDTO app, LinkedHashMap<String, Object> hyperParams, Long connectorRunId,
                                              Long cohortId, String keycloakId, boolean session) {
        ExecutionRunDTO run = setupBO.start(app, hyperParams, connectorRunId, cohortId, keycloakId);
        if (session) {
            progressBO.registerSession(run.getId());
        }
        return run;
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
        if (progressBO.completeBatch(upload.getRunId(), table)) {
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

    public CompletableFuture<TableData> expectBatch(Long batchId) {
        return progressBO.expectBatch(batchId);
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
    }
}
