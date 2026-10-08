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

    public static final Duration APP_OUTPUT_TIMEOUT = Duration.ofMinutes(5);
    private final ConcurrentHashMap<Long, CompletableFuture<TableData>> pendingBatches = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Long> batchSteps = new ConcurrentHashMap<>();
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

    public Optional<Long> cohortOf(Long runId) {
        return runId == null ? Optional.empty() : Optional.ofNullable(cohorts.get(runId));
    }

    public void registerBatch(Long batchId, Long stepId) {
        if (batchId != null) {
            pendingBatches.computeIfAbsent(batchId, id -> new CompletableFuture<>());
            batchSteps.put(batchId, stepId);
        }
    }

    public void failBatchesOf(Long stepId, String error) {
        batchSteps.forEach((batchId, batchStepId) -> {
            if (batchStepId.equals(stepId)) {
                failBatch(batchId, error);
            }
        });
    }

    public boolean isBatch(Long batchId) {
        return batchId != null && pendingBatches.containsKey(batchId);
    }

    // The entry stays until the result is awaited, so a result that arrives before awaitBatch is not lost
    public Uni<TableData> awaitBatch(Long batchId, Duration timeout) {
        CompletableFuture<TableData> future =
                pendingBatches.computeIfAbsent(batchId, id -> new CompletableFuture<>());
        future.whenComplete((output, error) -> {
            pendingBatches.remove(batchId, future);
            batchSteps.remove(batchId);
        });
        return Uni.createFrom().completionStage(future).ifNoItem().after(timeout).fail();
    }

    public boolean completeBatch(Long batchId, TableData outputData) {
        CompletableFuture<TableData> future = pendingBatches.get(batchId);
        if (future == null) {
            return false;
        }
        future.complete(outputData);
        return true;
    }

    public void failBatch(Long batchId, String error) {
        CompletableFuture<TableData> future = pendingBatches.get(batchId);
        if (future != null) {
            future.completeExceptionally(new IllegalStateException(error == null ? "The app reported an error" : error));
        }
    }

    public void cancelBatch(Long batchId) {
        batchSteps.remove(batchId);
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
