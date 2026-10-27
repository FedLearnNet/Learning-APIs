package bio.cosy.feddb.local.api.importer.run;

import bio.cosy.feddb.core.api.file.FileParsingSettingsDTO;
import bio.cosy.feddb.core.api.file.FileParsingType;
import bio.cosy.feddb.core.execution.run.dto.AppRunUploadData;
import bio.cosy.feddb.core.execution.run.types.RunStatusTypes;
import bio.cosy.feddb.local.api.importer.files.ConnectorFilesDTO;
import bio.cosy.feddb.local.api.importer.files.read.TabularFileReaderBO;
import bio.cosy.feddb.local.api.importer.files.table.TableData;
import bio.cosy.feddb.local.api.importer.files.upload.ConnectorFileUploadBO;
import bio.cosy.feddb.local.execution.run.ExecutionRunAO;
import bio.cosy.feddb.local.execution.run.ExecutionRunBO;
import bio.cosy.feddb.local.execution.run.ExecutionRunDTO;
import bio.cosy.feddb.local.execution.run.ExecutionRunEntity;
import io.quarkus.logging.Log;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class ConnectorAppRunProgressBO {

    @Inject
    ExecutionRunBO runBO;

    @Inject
    ExecutionRunAO runAO;

    @Inject
    TabularFileReaderBO fileReaderBO;

    @Inject
    ConnectorFileUploadBO uploadBO;

    // Runs whose app takes batches; it is not started when it connects and may connect again.
    private final Set<Long> sessionRuns = ConcurrentHashMap.newKeySet();

    // Attempts of those runs with their run, a finished batch does not end them.
    private final Map<Long, Long> sessionAttempts = new ConcurrentHashMap<>();

    // Table a run uploaded, until whoever waits for the run takes it.
    private final Map<Long, ConnectorAppOutputDTO> outputs = new ConcurrentHashMap<>();

    // Result of a batch by the id the app was started with; batch ids are negative, never an attempt id.
    private final Map<Long, CompletableFuture<TableData>> batches = new ConcurrentHashMap<>();

    /** The table the run uploaded once it ended, or why it has none; a run taking longer than the timeout is stopped. */
    public Uni<ConnectorAppOutputDTO> awaitOutput(Long runId, Duration timeout) {
        return runBO.awaitEnd(runId, timeout).map(last -> outputOf(runId, last));
    }

    // The first upload that reads as a table.
    public TableData tableOf(AppRunUploadData upload) {
        for (FileUpload file : upload.getFilesNullsafe()) {
            if (file == null || file.uploadedFile() == null) {
                continue;
            }
            TableData table = fileReaderBO.getFirstTableData(file.uploadedFile().toFile(), new FileParsingSettingsDTO(FileParsingType.CSV));
            if (table != null) {
                return table;
            }
        }
        throw new IllegalArgumentException("No uploaded output reads as a table");
    }

    /** Hands the table to whoever waits for the batch; false when the upload answers no batch. */
    public boolean completeBatch(Long batchId, TableData table) {
        CompletableFuture<TableData> batch = batchId != null ? batches.remove(batchId) : null;
        if (batch == null) {
            return false;
        }
        batch.complete(table);
        return true;
    }

    // The run's output for whoever waits for it, with the files kept for the cohort.
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void storeOutputTransactional(Long attemptId, TableData table, AppRunUploadData upload) {
        ExecutionRunEntity run = runAO.findOfAttemptOptional(attemptId).orElseThrow();
        outputs.put(run.getId(), new ConnectorAppOutputDTO(null, table, storeUploads(run, upload)));
        Log.infof("Run %d uploaded its output", run.getId());
    }

    /** Announces a batch before it is sent, so its result cannot arrive before anyone waits. */
    public CompletableFuture<TableData> expectBatch(Long batchId) {
        return batches.computeIfAbsent(batchId, id -> new CompletableFuture<>());
    }

    public void forgetBatch(Long batchId) {
        Optional.ofNullable(batches.remove(batchId)).ifPresent(batch -> batch.cancel(true));
    }

    public void registerSession(Long runId) {
        sessionRuns.add(runId);
    }

    public void forgetSession(Long runId) {
        sessionRuns.remove(runId);
    }

    public void forgetSessionAttempt(Long attemptId) {
        sessionAttempts.remove(attemptId);
    }

    public boolean isSession(Long runId) {
        return sessionRuns.contains(runId);
    }

    public boolean isSessionAttempt(Long attemptId) {
        return sessionAttempts.containsKey(attemptId);
    }

    /** The run of a session's attempt. */
    public Long sessionRunIdOf(Long attemptId) {
        return Optional.ofNullable(sessionAttempts.get(attemptId))
                .orElseThrow(() -> new AppTransformerSessionFailure("attempt " + attemptId + " serves no session"));
    }

    /**
     * The attempt of a session's run once its app connected; a run change follows every connect, as the app
     * reports in. Fails once the run ended.
     */
    public Uni<Long> connectedAttemptId(Long runId) {
        return runBO.subscribe(runId)
                .map(this::connectedAttemptIdOf)
                .select().where(Optional::isPresent)
                .map(Optional::get)
                .toUni()
                .onItem().ifNull().failWith(() -> new AppTransformerSessionFailure("ended before its app connected"))
                .invoke(attemptId -> sessionAttempts.put(attemptId, runId));
    }

    private Optional<Long> connectedAttemptIdOf(ExecutionRunDTO run) {
        if (run.hasEnded()) {
            throw new AppTransformerSessionFailure("ended as " + run.getStatus()
                    + (run.getFailure() != null ? ": " + run.getFailure().getMessage() : ""));
        }
        return run.getSteps().stream()
                .flatMap(step -> step.getAttempts().stream())
                .map(attempt -> attempt.getId())
                .max(Comparator.naturalOrder())
                .filter(id -> runBO.connectionOf(id).isPresent());
    }

    private ConnectorAppOutputDTO outputOf(Long runId, ExecutionRunDTO last) {
        ConnectorAppOutputDTO output = outputs.remove(runId);
        if (last == null || last.getStatus() != RunStatusTypes.FINISHED || output == null) {
            if (output != null) {
                output.getOutputData().close();
            }
            throw new IllegalStateException(last != null && last.getFailure() != null
                    ? last.getFailure().getMessage()
                    : "App run " + runId + " finished without output");
        }
        output.setRun(last);
        return output;
    }

    // Uploads of a run of a cohort are kept as files of the cohort, by output name.
    private LinkedHashMap<String, Object> storeUploads(ExecutionRunEntity run, AppRunUploadData upload) {
        LinkedHashMap<String, Object> stored = new LinkedHashMap<>();
        if (run.getCohort() == null) {
            return stored;
        }
        int index = 0;
        for (FileUpload file : upload.getFilesNullsafe()) {
            if (file == null || file.uploadedFile() == null) {
                continue;
            }
            String key = Optional.ofNullable(AppRunUploadData.keyOf(file)).filter(name -> !name.isBlank()).orElse("output" + index);
            String fileName = Optional.ofNullable(AppRunUploadData.nameOf(file)).filter(name -> !name.isBlank()).orElse(file.fileName());
            index++;
            try {
                ConnectorFilesDTO kept = uploadBO.uploadFileForCohort(run.getCohort().getId(), file.uploadedFile().toFile(),
                        fileName, run.getKeycloakId(), null);
                stored.put(key, kept.getId());
            } catch (RuntimeException e) {
                Log.errorf("Output %s of run %d could not be kept: %s", key, run.getId(), e.getMessage());
            }
        }
        return stored;
    }
}
