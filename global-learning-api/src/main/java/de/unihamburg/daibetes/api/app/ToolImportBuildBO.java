package de.unihamburg.daibetes.api.app;

import bio.cosy.feddb.core.api.pipeline.PipelineStatus;
import de.unihamburg.daibetes.api.build.pipeline.PipelineBO;
import de.unihamburg.daibetes.api.build.pipeline.PipelineCreateDTO;
import de.unihamburg.daibetes.api.build.pipeline.PipelineDTO;
import de.unihamburg.daibetes.api.build.pipeline.PipelineType;
import de.unihamburg.daibetes.api.build.pipeline.steps.PipelineStepDTO;
import de.unihamburg.daibetes.config.FLNetConfig;
import io.quarkus.logging.Log;
import io.smallrye.context.api.ManagedExecutorConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.context.ManagedExecutor;
import org.eclipse.microprofile.context.ThreadContext;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Runs the build pipeline for imported tools that have no image yet and publishes them afterwards.
 * An import only describes a tool, image and scan results are always created by this installation.
 */
@ApplicationScoped
public class ToolImportBuildBO {

    private static final Duration POLL_INTERVAL = Duration.ofSeconds(10);

    @Inject
    FLNetConfig config;

    @Inject
    FederatedAppBO appBO;

    @Inject
    PipelineBO pipelineBO;

    @Inject
    @ManagedExecutorConfig(cleared = ThreadContext.TRANSACTION)
    ManagedExecutor asyncExecutor;

    public void buildMissingImagesAsync(Collection<UUID> uniqueAppIds) {
        if (uniqueAppIds == null || uniqueAppIds.isEmpty()) {
            return;
        }
        List<UUID> ids = List.copyOf(uniqueAppIds);
        asyncExecutor.runAsync(() -> buildMissingImages(ids))
                .exceptionally(err -> {
                    Log.warnf(err, "Import builds failed");
                    return null;
                });
    }

    // one build at a time, a build is heavy and the tools do not depend on each other
    private void buildMissingImages(List<UUID> uniqueAppIds) {
        for (UUID uniqueAppId : uniqueAppIds) {
            try {
                Optional<FederatedAppBO.ImportBuildTarget> target = appBO.findImportBuildTargetTransactional(uniqueAppId);
                if (target.isEmpty()) {
                    continue;
                }
                buildAndPublish(uniqueAppId, target.get());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                Log.warnf("Import builds interrupted");
                return;
            } catch (Exception e) {
                Log.warnf(e, "Import build for app %s failed, it is retried on the next startup", uniqueAppId);
            }
        }
    }

    private void buildAndPublish(UUID uniqueAppId, FederatedAppBO.ImportBuildTarget target) throws InterruptedException {
        Duration timeout = config.toolImports().buildTimeout();
        Long versionId = target.versionId();
        String keycloakId = target.authorKeycloakId();
        // the build runs as the author of the app, like a build started in the UI
        PipelineCreateDTO create = new PipelineCreateDTO();
        create.setPipelineType(PipelineType.APP);
        create.setAppVersionId(versionId);
        create.setAutoStart(true);
        PipelineDTO pipeline = pipelineBO.createTransactional(create, keycloakId);
        Log.infof("Started import build for app %s in pipeline %d", uniqueAppId, pipeline.getId());

        Instant deadline = Instant.now().plus(timeout);
        while (!isFinished(pipeline.getPipelineStatus())) {
            if (Instant.now().isAfter(deadline)) {
                Log.warnf("Import build for app %s in pipeline %d did not finish within %s", uniqueAppId, pipeline.getId(), timeout);
                return;
            }
            Thread.sleep(POLL_INTERVAL.toMillis());
            pipeline = pipelineBO.getByIdTransactional(pipeline.getId(), keycloakId);
        }

        if (pipeline.getPipelineStatus() != PipelineStatus.SUCCESS) {
            Log.warnf("Import build for app %s in pipeline %d ended with %s%s", uniqueAppId, pipeline.getId(),
                    pipeline.getPipelineStatus(), describeFailedStep(pipeline));
            return;
        }
        appBO.publishImportedTransactional(versionId, config.toolImports().createUnpublishedVersion());
        Log.infof("Import build for app %s finished, version %d is published", uniqueAppId, versionId);
    }

    private boolean isFinished(PipelineStatus status) {
        return status == PipelineStatus.SUCCESS || status == PipelineStatus.FAILED || status == PipelineStatus.STOPPED;
    }

    private String describeFailedStep(PipelineDTO pipeline) {
        if (pipeline.getPipelineSteps() == null) {
            return "";
        }
        return pipeline.getPipelineSteps().stream()
                .filter(s -> s.getStepStatus() == PipelineStatus.FAILED)
                .findFirst()
                .map((PipelineStepDTO s) -> String.format(", step %s: %s", s.getName(), s.getErrorMessage()))
                .orElse("");
    }
}
