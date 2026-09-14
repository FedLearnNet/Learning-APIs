package bio.cosy.feddb.local.api.importer.run.execution;

import bio.cosy.feddb.local.api.importer.files.table.TableData;
import bio.cosy.feddb.local.api.importer.run.step.ConnectorRunStepDTO;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class ConnectorRunExecutionOutputAwaiter {

    private final ConcurrentHashMap<Long, CompletableFuture<ConnectorRunExecutionResult>> pending = new ConcurrentHashMap<>();


    private final ConcurrentHashMap<Long, Long> cohorts = new ConcurrentHashMap<>();

    public void register(Long runId, Long cohortId) {
        if (runId == null) {
            return;
        }
        pending.computeIfAbsent(runId, id -> new CompletableFuture<>());
        if (cohortId == null) {
            cohorts.remove(runId);
        } else {
            cohorts.put(runId, cohortId);
        }
    }

    /** Cohort registered for this step, empty when the outputs are not to be stored. */
    public Optional<Long> cohortOf(Long runId) {
        return runId == null ? Optional.empty() : Optional.ofNullable(cohorts.get(runId));
    }

    /**
     * Results of individual batches, keyed by the batch id the app was asked to run.
     *
     * <p>A batched transformer keeps one container for the whole import and feeds it batch after
     * batch, so the step id no longer identifies a single result. The app echoes the id it was
     * started with in its upload, which is what these futures are keyed by.</p>
     */
    private final ConcurrentHashMap<Long, CompletableFuture<TableData>> pendingBatches = new ConcurrentHashMap<>();

    /** Announces a batch before it is sent, so its result cannot arrive before anyone waits. */
    public void registerBatch(Long batchId) {
        if (batchId != null) {
            pendingBatches.computeIfAbsent(batchId, id -> new CompletableFuture<>());
        }
    }

    public boolean isBatch(Long batchId) {
        return batchId != null && pendingBatches.containsKey(batchId);
    }

    public Uni<TableData> awaitBatch(Long batchId, Duration timeout) {
        CompletableFuture<TableData> future =
                pendingBatches.computeIfAbsent(batchId, id -> new CompletableFuture<>());
        return Uni.createFrom().completionStage(future).ifNoItem().after(timeout).fail();
    }

    /** Hands a batch result to whoever is waiting. Returns false when the id is not a known batch. */
    public boolean completeBatch(Long batchId, TableData outputData) {
        CompletableFuture<TableData> future = pendingBatches.remove(batchId);
        if (future == null) {
            return false;
        }
        future.complete(outputData);
        return true;
    }

    public void cancelBatch(Long batchId) {
        CompletableFuture<TableData> future = pendingBatches.remove(batchId);
        if (future != null && !future.isDone()) {
            future.cancel(true);
        }
    }

    public Uni<ConnectorRunExecutionResult> awaitOutput(Long runId) {
        CompletableFuture<ConnectorRunExecutionResult> future =
                pending.computeIfAbsent(runId, id -> new CompletableFuture<>());

        return Uni.createFrom().completionStage(future)
                .ifNoItem().after(Duration.ofMinutes(5)).fail();
    }

    public void complete(Long runId, ConnectorRunStepDTO dto, TableData outputData) {
        complete(runId, dto, outputData, new LinkedHashMap<>());
    }

    public void complete(Long runId,
                         ConnectorRunStepDTO dto,
                         TableData outputData,
                         LinkedHashMap<String, Object> storedOutputs) {
        ConnectorRunExecutionResult result = new ConnectorRunExecutionResult(dto, outputData, storedOutputs);
        cohorts.remove(runId);
        CompletableFuture<ConnectorRunExecutionResult> future = pending.remove(runId);
        if (future != null && !future.isDone()) {
            future.complete(result);
        }
    }

    public void fail(Long runId, Throwable throwable) {
        cohorts.remove(runId);
        CompletableFuture<ConnectorRunExecutionResult> future = pending.remove(runId);
        if (future != null && !future.isDone()) {
            future.completeExceptionally(throwable);
        }
    }

    public void cancel(Long runId) {
        cohorts.remove(runId);
        CompletableFuture<ConnectorRunExecutionResult> future = pending.remove(runId);
        if (future != null && !future.isDone()) {
            future.cancel(true);
        }
    }
}
