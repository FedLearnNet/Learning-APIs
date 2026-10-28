package de.unihamburg.daibetes.api.build.pipeline;

import bio.cosy.feddb.core.api.pipeline.PipelineStatus;
import bio.cosy.feddb.core.api.pipeline.PipelineRunMode;
import bio.cosy.feddb.core.services.orch.clients.ContainerServiceClient;
import bio.cosy.feddb.core.services.orch.clients.DockerServiceClient;
import de.unihamburg.daibetes.api.app.FederatedAppAO;
import de.unihamburg.daibetes.api.app.FederatedAppBO;
import de.unihamburg.daibetes.api.app.FederatedAppEntity;
import de.unihamburg.daibetes.api.app.ToolExternalHandler;
import de.unihamburg.daibetes.api.app.version.FederatedAppVersionAO;
import de.unihamburg.daibetes.api.app.version.FederatedAppVersionEntity;
import de.unihamburg.daibetes.api.build.pipeline.steps.StepName;
import de.unihamburg.daibetes.config.FLNetConfig;
import de.unihamburg.daibetes.config.ToolImportBuildingOnStart;
import io.quarkus.logging.Log;
import io.smallrye.context.api.ManagedExecutorConfig;
import io.smallrye.mutiny.Uni;
import io.smallrye.reactive.messaging.MutinyEmitter;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.TransactionPhase;
import jakarta.inject.Inject;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.microprofile.context.ManagedExecutor;
import org.eclipse.microprofile.context.ThreadContext;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.OnOverflow;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class ToolImportBuildBO {

    private static final String TOOL_IMPORT_BUILD_CHANNEL = "tool-import-builds";

    @Inject
    FLNetConfig config;

    @Inject
    FederatedAppVersionAO versionAO;

    @Inject
    FederatedAppAO appAO;

    @Inject
    PipelineAO pipelineAO;

    @Inject
    PipelineBO pipelineBO;

    @Inject
    ToolSourceCheckBO sourceCheckBO;

    @Inject
    FederatedAppBO appBO;

    @Inject
    ToolExternalHandler toolExternalHandler;

    @Inject
    @RestClient
    DockerServiceClient dockerClient;

    @Inject
    @RestClient
    ContainerServiceClient containerClient;

    @Inject
    Event<PipelineRebuildDTO> buildRequests;

    @Inject
    @ManagedExecutorConfig(cleared = ThreadContext.TRANSACTION)
    ManagedExecutor executor;

    @Inject
    @Channel(TOOL_IMPORT_BUILD_CHANNEL)
    @OnOverflow(OnOverflow.Strategy.UNBOUNDED_BUFFER)
    MutinyEmitter<PipelineRebuildDTO> buildEmitter;

    private final Set<Long> queuedVersions = ConcurrentHashMap.newKeySet();

    public void enqueue(FederatedAppVersionEntity version, boolean existsInStore) {
        if (version.isRebuilding() || config.toolImports().buildOnImport() == ToolImportBuildingOnStart.NEVER) {
            return;
        }
        PipelineRebuildDTO request = new PipelineRebuildDTO();
        request.setPipelineType(PipelineType.APP);
        request.setAppVersionId(version.getId());
        request.setExistsInStore(existsInStore);
        buildRequests.fire(request);
    }

    public void enqueueRescan(FederatedAppVersionEntity version) {
        if (!version.hasImage() || version.isRebuilding() || pipelineAO.existsActiveByAppVersionId(version.getId())) {
            return;
        }
        PipelineRebuildDTO request = new PipelineRebuildDTO();
        request.setPipelineType(PipelineType.APP);
        request.setAppVersionId(version.getId());
        request.setRunMode(PipelineRunMode.SCAN_ONLY);
        request.setExistsInStore(true);
        buildRequests.fire(request);
    }

    void queue(@Observes(during = TransactionPhase.AFTER_SUCCESS) PipelineRebuildDTO request) {
        if (!queuedVersions.add(request.getAppVersionId())) {
            return;
        }
        Uni.createFrom().deferred(() -> buildEmitter.send(request))
                .onFailure().invoke(e -> queuedVersions.remove(request.getAppVersionId()))
                .subscribe().with(ignored -> {
                        },
                        e -> Log.warnf(e, "Failed to queue imported tool version %d", request.getAppVersionId()));
    }

    @Incoming(TOOL_IMPORT_BUILD_CHANNEL)
    public Uni<Void> build(PipelineRebuildDTO request) {
        // The ordered channel waits for completion before starting the next build.
        return Uni.createFrom().item(() -> createBuild(request))
                .runSubscriptionOn(executor)
                .onItem().transformToUni(run -> startBuild(run, request))
                .onTermination().invoke(() -> queuedVersions.remove(request.getAppVersionId()))
                .onFailure().invoke(e -> Log.warnf(e, "Failed to build imported tool version %d", request.getAppVersionId()))
                .onFailure().recoverWithNull();
    }

    private Uni<Void> startBuild(CurrentPipelineRuns run, PipelineRebuildDTO request) {
        if (run == null) {
            return Uni.createFrom().voidItem();
        }
        return Uni.createFrom().item(() -> startPipelineTransactional(run.getPipelineId()))
                .runSubscriptionOn(executor)
                .onItem().transformToUni(ignored -> awaitBuild(run))
                .onFailure().call(e -> Uni.createFrom().voidItem()
                        .invoke(() -> failBuild(run)).runSubscriptionOn(executor))
                .onTermination().call(() -> Uni.createFrom().voidItem()
                        .invoke(() -> finishBuild(run, request)).runSubscriptionOn(executor));
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    PipelineDTO startPipelineTransactional(Long pipelineId) {
        return pipelineBO.start(pipelineId);
    }

    private CurrentPipelineRuns createBuild(PipelineRebuildDTO request) {
        FederatedAppVersionEntity version = findVersionTransactional(request.getAppVersionId());
        if (version == null || !shouldBuild(version, request)) {
            return null;
        }
        return claimBuildTransactional(request);
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    FederatedAppVersionEntity findVersionTransactional(Long appVersionId) {
        return versionAO.findById(appVersionId);
    }

    // Locks the version, so only one build of it runs.
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    CurrentPipelineRuns claimBuildTransactional(PipelineRebuildDTO request) {
        FederatedAppVersionEntity current = versionAO.findById(request.getAppVersionId(), LockModeType.PESSIMISTIC_WRITE);
        if (current == null || current.isRebuilding()
                || (request.getRunMode() != PipelineRunMode.SCAN_ONLY && StringUtils.isBlank(current.getFederatedApp().getSourceUrl()))
                || pipelineAO.existsActiveByAppVersionId(current.getId())) {
            return null;
        }
        request.setAutoStart(false);
        if (request.getRunMode() != PipelineRunMode.SCAN_ONLY) {
            current.setImageName(null);
            current.setPublishInfo(null);
        }
        PipelineDTO pipeline = pipelineBO.create(request);
        current.setRebuilding(true);
        CurrentPipelineRuns run = new CurrentPipelineRuns();
        run.setPipelineId(pipeline.getId());
        run.setToken(pipeline.getSecret());
        return run;
    }

    private Uni<Void> awaitBuild(CurrentPipelineRuns run) {
        return Uni.createFrom().item(() -> pipelineStatusTransactional(run))
                .runSubscriptionOn(executor)
                .repeat().withDelay(Duration.ofSeconds(2)).indefinitely()
                .select().where(status -> status != null && status != PipelineStatus.PENDING && status != PipelineStatus.RUNNING)
                .select().first().toUni()
                .ifNoItem().after(Duration.ofHours(1)).fail()
                .onItem().invoke(status -> Log.infof("Imported tool build pipeline %d finished with status %s", run.getPipelineId(), status))
                .replaceWithVoid();
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    PipelineStatus pipelineStatusTransactional(CurrentPipelineRuns run) {
        return pipelineAO.findByIdAndSecretOptional(run.getPipelineId(), run.getToken(), LockModeType.PESSIMISTIC_READ)
                .orElseThrow(() -> new IllegalStateException("Imported tool build pipeline not found"))
                .getPipelineStatus();
    }

    private void failBuild(CurrentPipelineRuns run) {
        try {
            failBuildTransactional(run);
        } catch (Exception e) {
            Log.warnf(e, "Failed to mark imported tool build pipeline %d as failed", run.getPipelineId());
        }
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    void failBuildTransactional(CurrentPipelineRuns run) {
        PipelineEntity pipeline = pipelineAO.findByIdAndSecretOptional(run.getPipelineId(), run.getToken(), LockModeType.PESSIMISTIC_WRITE)
                .orElseThrow(() -> new IllegalStateException("Imported tool build pipeline not found"));
        PipelineStatusUpdateDTO failure = new PipelineStatusUpdateDTO();
        failure.setStepName(pipeline.getSteps().stream()
                .filter(step -> step.getStepStatus() == PipelineStatus.RUNNING)
                .map(step -> step.getName()).findFirst().orElse(StepName.PREFLIGHT));
        failure.setStatus(PipelineStatus.FAILED);
        failure.setErrorCode("IMPORT_BUILD_FAILED");
        failure.setErrorMessage("Imported tool build failed or exceeded the time limit");
        pipelineBO.processStatusUpdate(run.getPipelineId(), run.getToken(), failure);
    }

    // The pipeline container is auto-removed on exit, but its dedicated network is only removed by orch's cleanup
    private void cleanupContainer(CurrentPipelineRuns run) {
        try {
            String containerId = containerIdTransactional(run);
            if (StringUtils.isNotBlank(containerId)) {
                containerClient.cleanupWorkflow(containerId, true);
            }
        } catch (Exception e) {
            Log.warnf(e, "Failed to clean up imported tool build pipeline %d", run.getPipelineId());
        }
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    String containerIdTransactional(CurrentPipelineRuns run) {
        return pipelineAO.findByIdAndSecretOptional(run.getPipelineId(), run.getToken(), LockModeType.PESSIMISTIC_READ)
                .map(PipelineEntity::getContainerId).orElse(null);
    }

    private void finishBuild(CurrentPipelineRuns run, PipelineRebuildDTO request) {
        PipelineStatus status = finishBuildTransactional(run, request);
        if (status != null) {
            cleanupContainer(run);
        }
        if (status != null && status != PipelineStatus.FAILED && status != PipelineStatus.STOPPED) {
            replaceBuildInfo(request);
        }
        if (status != PipelineStatus.FAILED || request.isExistsInStore()) {
            return;
        }
        try {
            removeFailedVersionTransactional(request.getAppVersionId());
        } catch (Exception e) {
            Log.warnf(e, "Failed to remove newly imported app version %d after its build failed", request.getAppVersionId());
        }
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public PipelineStatus finishBuildTransactional(CurrentPipelineRuns run, PipelineRebuildDTO request) {
        PipelineEntity pipeline = pipelineAO.findByIdAndSecretOptional(run.getPipelineId(), run.getToken(), LockModeType.PESSIMISTIC_READ)
                .orElse(null);
        if (pipeline == null || pipeline.getPipelineStatus() == PipelineStatus.PENDING
                || pipeline.getPipelineStatus() == PipelineStatus.RUNNING) {
            return null;
        }
        FederatedAppVersionEntity version = versionAO.findById(request.getAppVersionId(), LockModeType.PESSIMISTIC_WRITE);
        if (version != null && !pipelineAO.existsActiveByAppVersionId(version.getId())) {
            version.setRebuilding(false);
        }
        return pipeline.getPipelineStatus();
    }

    private void replaceBuildInfo(PipelineRebuildDTO request) {
        if (!config.toolImports().replaceBuildInfo() || request.getRunMode() == PipelineRunMode.SCAN_ONLY) {
            return;
        }
        try {
            replaceBuildInfoTransactional(request.getAppVersionId());
        } catch (Exception e) {
            Log.warnf(e, "Failed to replace build information of imported tool version %d", request.getAppVersionId());
        }
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    void replaceBuildInfoTransactional(Long appVersionId) {
        FederatedAppVersionEntity version = versionAO.findById(appVersionId);
        if (version == null || !version.hasImage()) {
            return;
        }
        toolExternalHandler.replaceBuildInfoInJson(appBO.getMyAppById(version.getFederatedApp().getId()))
                .ifPresent(path -> Log.infof("Replaced build information of imported tool version %d in %s", appVersionId, path));
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void removeFailedVersionTransactional(Long appVersionId) {
        if(!config.toolImports().removeImageOnBuildFailure()) {
            return;
        }
        FederatedAppVersionEntity version = versionAO.findById(appVersionId, LockModeType.PESSIMISTIC_WRITE);
        if (version == null || version.isRebuilding()
                || pipelineAO.existsActiveByAppVersionId(appVersionId)) {
            return;
        }
        FederatedAppEntity app = appAO.findById(version.getFederatedApp().getId(), LockModeType.PESSIMISTIC_WRITE);
        pipelineAO.detachAppVersion(appVersionId);
        app.getVersions().remove(version);
        versionAO.delete(version);
        versionAO.flush();
        Log.infof("Removed newly imported app version %d after its build failed", appVersionId);
        if (!versionAO.existsByAppId(app.getId())) {
            appAO.delete(app);
            Log.infof("Removed app %d because it has no remaining versions", app.getId());
        }
    }

    private boolean shouldBuild(FederatedAppVersionEntity version, PipelineRebuildDTO request) {
        if (config.pipelineBuilderImage().filter(StringUtils::isNotBlank).isEmpty()) {
            Log.warnf("Skipping imported tool builds: pipeline builder image is not configured");
            return false;
        }
        if (request.getRunMode() == PipelineRunMode.SCAN_ONLY) {
            return version.hasImage();
        }

        return switch (config.toolImports().buildOnImport()) {
            case ALWAYS -> true;
            case IF_NOT_EXISTS_IN_STORE -> !request.isExistsInStore();
            case IF_NOT_EXISTS_IN_REGISTRY -> {
                if (!version.hasImage()) {
                    Log.infof("Building imported tool version %d: it has no image yet", version.getId());
                    yield true;
                }
                try {
                    if (!dockerClient.imageExistsInRegistry(version.getImageName())) {
                        Log.infof("Building imported tool version %d: image %s is missing in the registry",
                                version.getId(), version.getImageName());
                        yield true;
                    }
                } catch (Exception e) {
                    Log.warnf(e, "Failed to check registry image for imported tool version %d; proceeding with build", version.getId());
                    yield true;
                }
                if (sourceCheckBO.isOutdated(version)) {
                    Log.infof("Building imported tool version %d: image %s is behind its source",
                            version.getId(), version.getImageName());
                    yield true;
                }
                yield false;
            }
            case NEVER -> false;
        };
    }

}
