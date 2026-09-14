package de.unihamburg.daibetes.api.analysis.run;

import bio.cosy.feddb.core.api.model.prediction.DataAnalysisPredictionDTO;
import bio.cosy.feddb.core.api.run.RunStatusTypes;
import de.unihamburg.daibetes.api.analysis.prediction.DataAnalysisPredictionBO;
import de.unihamburg.daibetes.config.FLNetConfig;
import io.quarkus.logging.Log;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Duration;
import java.util.concurrent.TimeoutException;

@ApplicationScoped
public class DataAnalysisMonitor {
    @Inject
    FLNetConfig config;

    @Inject
    DataAnalysisPredictionBO modelPredictionBO;

    public void start(Long predictionId) {
        Uni.createFrom().voidItem()
                .runSubscriptionOn(Infrastructure.getDefaultWorkerPool())
                .call(() -> monitor(predictionId))
                .onFailure().invoke(t -> failPrediction(predictionId, "Monitoring failed: " + t.getMessage(), t))
                .subscribe().with(
                        ignored -> {
                        },
                        failure -> {
                        }
                );
    }

    private Uni<Void> monitor(Long dataAnalysisPredictionId) {
        return Uni.createFrom().item(() -> {
                    try {
                        waitForRunStartup(dataAnalysisPredictionId);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException("Monitoring thread interrupted", e);
                    } catch (TimeoutException e) {
                        throw new IllegalStateException(e.getMessage(), e);
                    }
                    return null;
                })
                .replaceWithVoid();
    }


    private void failPrediction(Long dataAnalysisPredictionId, String msg, Throwable t) {
        Log.errorf(t, "Data analysis run %d failed while waiting for websocket startup: %s", dataAnalysisPredictionId, msg);
        modelPredictionBO.updateAndNotify(dataAnalysisPredictionId, RunStatusTypes.ERROR, msg);
    }

    private void waitForRunStartup(Long dataAnalysisPredictionId) throws InterruptedException, TimeoutException {
        Duration startupTimeout = config.dataAnalysis().inactivityTimeout();
        long timeoutAt = System.nanoTime() + startupTimeout.toNanos();
        long sleepMillis = Math.max(250L, Math.min(1_000L, startupTimeout.toMillis() / 10));

        while (System.nanoTime() < timeoutAt) {
            DataAnalysisPredictionDTO prediction = modelPredictionBO.getById(dataAnalysisPredictionId);
            if (prediction == null || prediction.getStatus() == null || prediction.isFinished()) {
                return;
            }
            if (!RunStatusTypes.canBeStartedStatus(prediction.getStatus())) {
                return;
            }
            Thread.sleep(sleepMillis);
        }

        throw new TimeoutException(String.format(
                "Run %d did not connect to DataAnalysisRunWebsocketService and start within %s",
                dataAnalysisPredictionId,
                startupTimeout
        ));
    }
}
