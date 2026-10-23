package de.unihamburg.daibetes.api.build.pipeline;

import bio.cosy.feddb.core.api.app.AppPublishInfoDTO;
import bio.cosy.feddb.core.api.pipeline.PipelineStatus;
import bio.cosy.feddb.core.api.pipeline.PipelineRunMode;
import bio.cosy.feddb.core.base.BaseBo;
import bio.cosy.feddb.core.security.Scope;
import bio.cosy.feddb.core.security.ToolApiKeyService;
import bio.cosy.feddb.core.services.orch.clients.ContainerServiceClient;
import bio.cosy.feddb.core.services.orch.dto.CreateContainerResponseDTO;
import bio.cosy.feddb.core.services.orch.dto.StartPipelineDTO;
import de.unihamburg.daibetes.api.app.FederatedAppEntity;
import de.unihamburg.daibetes.api.app.author.FederatedAppAuthorBO;
import de.unihamburg.daibetes.api.app.version.FederatedAppVersionEntity;
import de.unihamburg.daibetes.api.build.pipeline.steps.PipelineStepBO;
import de.unihamburg.daibetes.api.build.pipeline.steps.PipelineStepEntity;
import de.unihamburg.daibetes.api.build.pipeline.steps.StepName;
import de.unihamburg.daibetes.api.model.access.ModelAccessBO;
import de.unihamburg.daibetes.api.model.sub.ModelSubEntity;
import de.unihamburg.daibetes.api.model.sub.file.ModelSubFileBO;
import de.unihamburg.daibetes.config.FLNetConfig;
import io.quarkus.logging.Log;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import io.smallrye.mutiny.operators.multi.processors.BroadcastProcessor;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotFoundException;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class PipelineBO extends BaseBo<PipelineDTO, PipelineEntity, PipelineAO, PipelineMapper> {

    private static final BroadcastProcessor<PipelineDTO> UPDATES = BroadcastProcessor.create();

    @Inject
    @RestClient
    ContainerServiceClient containerClient;

    @Inject
    ToolApiKeyService toolApiKeyService;

    @Inject
    PipelineStepBO stepBO;

    @Inject
    FederatedAppAuthorBO appAuthorBO;

    @Inject
    ModelAccessBO modelAccessBO;

    @Inject
    ModelSubFileBO modelSubFileBO;

    @Inject
    FLNetConfig config;

    @ConfigProperty(name = "quarkus.http.port")
    protected int serverPort;

    public PipelineDTO create(PipelineCreateDTO dto, String keycloakId) {
        if (dto.getPipelineType().equals(PipelineType.MODEL)) {
            if (dto.getModelSubId() == null) {
                throw new BadRequestException("Model subscription ID must be provided for model pipelines");
            }
            if (!modelAccessBO.hasUserAnyRightsBySubId(keycloakId, dto.getModelSubId())) {
                throw new ForbiddenException();
            }
        } else {
            if (dto.getAppVersionId() == null) {
                throw new BadRequestException("App version ID must be provided for app pipelines");
            }
            if (!appAuthorBO.isUserAuthorVersionId(keycloakId, dto.getAppVersionId())) {
                throw new ForbiddenException();
            }
        }
        return create(dto);
    }

    public PipelineDTO create(PipelineCreateDTO dto) {
        PipelineDTO pipeline = new PipelineDTO();
        pipeline.setPipelineType(dto.getPipelineType());
        pipeline.setRunMode(dto.getRunMode());
        pipeline.setPipelineStatus(PipelineStatus.PENDING);
        pipeline.setSecret(RandomStringUtils.secureStrong().nextAlphabetic(32));
        if (dto.getPipelineType().equals(PipelineType.MODEL)) {
            if (dto.getModelSubId() == null) {
                throw new BadRequestException("Model subscription ID must be provided for model pipelines");
            }
            pipeline.setModelSubId(dto.getModelSubId());
        } else {
            if (dto.getAppVersionId() == null) {
                throw new BadRequestException("App version ID must be provided for app pipelines");
            }
            pipeline.setAppVersionId(dto.getAppVersionId());
        }
        if (dto.getRunMode() == PipelineRunMode.LOCAL_BUILD && dto.getPipelineType() != PipelineType.MODEL) {
            throw new BadRequestException("LOCAL_BUILD requires uploaded model files");
        }
        if (dto.getRunMode() == PipelineRunMode.SCAN_ONLY) {
            String imageName;
            if (dto.getPipelineType() == PipelineType.MODEL) {
                ModelSubEntity modelSub = ao.getEntityManager().find(ModelSubEntity.class, dto.getModelSubId());
                imageName = modelSub == null ? null : modelSub.getImageName();
            } else {
                if (ao.existsActiveByAppVersionId(dto.getAppVersionId())) {
                    throw new BadRequestException("An active pipeline already exists for this tool version");
                }
                FederatedAppVersionEntity appVersion = ao.getEntityManager().find(FederatedAppVersionEntity.class, dto.getAppVersionId());
                imageName = appVersion == null ? null : appVersion.getImageName();
            }
            if (StringUtils.isBlank(imageName)) {
                throw new BadRequestException("SCAN_ONLY requires an existing image");
            }
        }
        PipelineDTO created = setPipelineEntityCreated(pipeline);
        if (dto.getAutoStart()) {
            return start(created.getId());
        }
        return created;
    }

    // For the initial creation of the entity before the pipeline is started
    @Transactional
    public PipelineDTO setPipelineEntityCreated(PipelineDTO dto) {
        return stepBO.createSteps(create(dto));
    }

    public PipelineDTO start(Long id, String keycloakId) {

        Optional<PipelineEntity> entityOptional = ao.findByIdOptional(id);
        if (entityOptional.isEmpty()) {
            throw new NotFoundException(String.format("Property with the ID %s not found", id));
        }
        userHasRights(entityOptional.get(), keycloakId);
        return start(id);
    }

    public PipelineDTO start(Long id) {
        if (config.pipelineBuilderImage().isEmpty()) {
            throw new IllegalStateException("Pipeline builder image is not configured");
        }
        String builderImage = config.pipelineBuilderImage().get();
        Optional<PipelineEntity> entityOptional = ao.findByIdOptional(id);
        if (entityOptional.isEmpty()) {
            throw new NotFoundException(String.format("Property with the ID %s not found", id));
        }
        PipelineEntity entity = entityOptional.get();
        String apiKey = toolApiKeyService.issue(Scope.PIPELINE_RUN, id);
        StartPipelineDTO startApp = new StartPipelineDTO();
        startApp.setPipelineId(id);
        startApp.setAppImage(builderImage);
        startApp.setRunMode(entity.getRunMode());
        startApp.addEnvironment(apiKey, id, entity.getSecret());
        Log.info("Starting container for pipeline " + id);
        CreateContainerResponseDTO resp;
        try {
            resp = containerClient.startPipeline(startApp, true, "", serverPort);
        } catch (Exception e) {
            String statusCode = e.getMessage() != null ? e.getMessage().split(" ")[0] : "Unknown";
            Log.error("Failed to start container (Status code: " + statusCode + "): " + e.getMessage(), e);
            toolApiKeyService.revoke(Scope.PIPELINE_RUN, id);
            throw new IllegalStateException("Failed to start container: " + e.getMessage(), e);
        }
        String containerId = resp.getId();
        if (containerId == null) {
            throw new IllegalStateException("Failed to start container for pipeline " + id);
        }
        return setPipelineEntityRunning(id, containerId);
    }

    @Transactional
    public PipelineDTO setPipelineEntityRunning(Long id, String containerId) {
        PipelineEntity entity = ao.findByIdOptional(id, LockModeType.PESSIMISTIC_WRITE)
                .orElseThrow(() -> new NotFoundException(String.format("Pipeline with ID %s not found", id)));
        entity.setContainerId(containerId);
        updateRebuilding(entity);
        return mapper.entityToDto(entity);
    }

    public List<PipelineDTO> getAll(String keycloakId, Long appVersionId, Long appId, Long modelSubId) {
        List<PipelineEntity> entities = ao.listAll();
        entities.removeIf(entity -> {
            try {
                userHasRights(entity, keycloakId);
                if (modelSubId != null && (entity.getModelSub() == null || !entity.getModelSub().getId().equals(modelSubId))) {
                    return true;
                }
                if (appId != null) {
                    FederatedAppEntity appEntity = getFederatedAppEntity(entity);
                    if (!appEntity.getId().equals(appId)) {
                        return true;
                    }
                }
                return appVersionId != null && (entity.getAppVersion() == null || !entity.getAppVersion().getId().equals(appVersionId));
            } catch (NotFoundException | ForbiddenException e) {
                return true;
            }
        });
        entities.sort(Comparator.comparing(PipelineEntity::getId).reversed());
        return mapper.entitiesToDtos(entities);
    }

    public PipelineDTO getById(Long id, String keycloakId) {
        Optional<PipelineEntity> entityOptional = ao.findByIdOptional(id);
        if (entityOptional.isEmpty()) {
            throw new NotFoundException(String.format("Property with the ID %s not found", id));
        }
        userHasRights(entityOptional.get(), keycloakId);
        return mapper.entityToDto(entityOptional.get());
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public PipelineDTO getByIdTransactional(Long id, String keycloakId) {
        Optional<PipelineEntity> entityOptional = ao.findByIdOptional(id);
        if (entityOptional.isEmpty()) {
            throw new NotFoundException(String.format("Property with the ID %s not found", id));
        }
        userHasRights(entityOptional.get(), keycloakId);
        return mapper.entityToDto(entityOptional.get());
    }

    public Multi<PipelineDTO> stream(Long id, String keycloakId) {
        return Uni.createFrom().item(() -> getByIdTransactional(id, keycloakId))
                .runSubscriptionOn(Infrastructure.getDefaultExecutor())
                .onItem().transformToMulti(initial ->
                        Multi.createBy().merging().streams(
                                Multi.createFrom().item(initial),
                                UPDATES
                                        .select().where(dto -> dto != null && dto.getId() != null && dto.getId().equals(id))
                                        .onOverflow().drop()
                        )
                );
    }


    public PipelineRunInfoDTO getPipelineRunInfo(Long id, String secret) {
        Optional<PipelineEntity> entityOptional = ao.findByIdAndSecretOptional(id, secret, LockModeType.PESSIMISTIC_READ);
        if (entityOptional.isEmpty()) {
            throw new NotFoundException(String.format("Property with the ID %s not found", id));
        }
        PipelineEntity entity = entityOptional.get();
        PipelineRunInfoDTO dto = new PipelineRunInfoDTO();
        FederatedAppEntity appEntity = getFederatedAppEntity(entity);
        dto.setGitRepoUrl(appEntity.getSourceUrl());
        dto.setImageName(appEntity.getSlug());
        if (entity.getAppVersion() != null && entity.getAppVersion().getPublishInfo() != null) {
            dto.setCommitHash(entity.getAppVersion().getPublishInfo().getCommitHash());
        }
        if (entity.getRunMode() == PipelineRunMode.SCAN_ONLY) {
            String imageName = entity.getPipelineType() == PipelineType.MODEL
                    ? entity.getModelSub().getImageName() : entity.getAppVersion().getImageName();
            if (StringUtils.isBlank(imageName)) {
                throw new BadRequestException("SCAN_ONLY requires an existing image");
            }
            dto.setImageReference(imageName);
        }
        return dto;
    }

    public PipelineDTO processStatusUpdate(Long id, String secret, PipelineStatusUpdateDTO dto) {
        Optional<PipelineEntity> entityOptional = ao.findByIdAndSecretOptional(id, secret, LockModeType.PESSIMISTIC_WRITE);
        if (entityOptional.isEmpty()) {
            throw new NotFoundException(String.format("Property with the ID %s not found", id));
        }
        PipelineEntity entity = entityOptional.get();
        stepBO.updateStep(entity, dto);
        updateOverallPipelineStatus(entity);
        PipelineDTO updated = mapper.entityToDto(entity);
        UPDATES.onNext(updated);
        return updated;
    }

    public PipelineDTO processSummary(Long id, String secret, AppPublishInfoDTO dto) {
        Optional<PipelineEntity> entityOptional = ao.findByIdAndSecretOptional(id, secret, LockModeType.PESSIMISTIC_WRITE);
        if (entityOptional.isEmpty()) {
            throw new NotFoundException(String.format("Property with the ID %s not found", id));
        }
        PipelineEntity entity = entityOptional.get();
        if (entity.getRunMode() == PipelineRunMode.SCAN_ONLY) {
            AppPublishInfoDTO previous = entity.getPipelineType() == PipelineType.MODEL
                    ? entity.getModelSub().getPublishInfo() : entity.getAppVersion().getPublishInfo();
            if (previous != null) {
                // SCAN_ONLY rescans the existing image for vulnerabilities without rebuilding or running
                // SAST/malware checks.
                dto.setCommitHash(previous.getCommitHash());
                dto.setFilePaths(previous.getFilePaths());
                if (dto.getSastScanResult() == null) {
                    dto.setSastScanResult(previous.getSastScanResult());
                }
                if (dto.getMalwareScanResult() == null) {
                    dto.setMalwareScanResult(previous.getMalwareScanResult());
                }
            }
        }
        setScanTimes(entity, dto);
        entity.setPublishInfo(dto);
        if (entity.getRunMode() != PipelineRunMode.SCAN_ONLY && StringUtils.isNotBlank(dto.getCommitHash())) {
            entity.setDockerTag(dto.getCommitHash());
        }
        ao.persist(entity);
        PipelineDTO updated = mapper.entityToDto(entity);
        UPDATES.onNext(updated);
        return updated;
    }

    // Only set it if the step got rerun
    private void setScanTimes(PipelineEntity entity, AppPublishInfoDTO dto) {
        PipelineStepEntity scanStep = findStep(entity, StepName.SCAN_IMAGE);
        if (scanStep != null && dto.getVulnerabilityScanResult() != null) {
            dto.getVulnerabilityScanResult().setStartedAt(scanStep.getStartedAt());
            dto.getVulnerabilityScanResult().setFinishedAt(finishedAt(scanStep));
        }
        PipelineStepEntity sastStep = findStep(entity, StepName.SAST);
        if (sastStep != null && dto.getSastScanResult() != null) {
            dto.getSastScanResult().setStartedAt(sastStep.getStartedAt());
            dto.getSastScanResult().setFinishedAt(finishedAt(sastStep));
        }
        PipelineStepEntity malwareStep = findStep(entity, StepName.MALWARE_CHECK);
        if (malwareStep != null && dto.getMalwareScanResult() != null) {
            dto.getMalwareScanResult().setStartedAt(malwareStep.getStartedAt());
            dto.getMalwareScanResult().setFinishedAt(finishedAt(malwareStep));
        }
    }

    private PipelineStepEntity findStep(PipelineEntity entity, StepName name) {
        return entity.getSteps().stream()
                .filter(step -> step.getName() == name)
                .findFirst()
                .orElse(null);
    }

    private Date finishedAt(PipelineStepEntity step) {
        return step.getFinishedAt() != null ? step.getFinishedAt() : new Date();
    }


    private void updateOverallPipelineStatus(PipelineEntity pipeline) {
        boolean hasFailedStep = pipeline.getSteps().stream()
                .anyMatch(s -> s.getStepStatus() == PipelineStatus.FAILED);

        if (hasFailedStep) {
            pipeline.setPipelineStatus(PipelineStatus.FAILED);
            updateRebuilding(pipeline);
            toolApiKeyService.revoke(Scope.PIPELINE_RUN, pipeline.getId());
            return;
        }
        boolean allStepsSucceeded = pipeline.getSteps().stream()
                .filter(f -> !f.getName().equals(StepName.PREFLIGHT))
                .allMatch(s -> s.getStepStatus() == PipelineStatus.SUCCESS || s.getStepStatus() == PipelineStatus.WARNING);

        boolean succeeded = allStepsSucceeded && pipeline.getSteps().stream()
                .anyMatch(step -> step.getName() == StepName.COLLECT_SUMMARY);
        pipeline.setPipelineStatus(succeeded ? PipelineStatus.SUCCESS : PipelineStatus.RUNNING);
        pipeline.setUpdatedAt(new Date());
        updateRebuilding(pipeline);
        if (succeeded) {
            setImageTag(pipeline);
        }
        ao.persist(pipeline);
        if (succeeded) {
            toolApiKeyService.revoke(Scope.PIPELINE_RUN, pipeline.getId());
        }
    }

    private void updateRebuilding(PipelineEntity pipeline) {
        if (pipeline.getAppVersion() != null) {
            pipeline.getAppVersion().setRebuilding(pipeline.getPipelineStatus() == PipelineStatus.PENDING
                    || pipeline.getPipelineStatus() == PipelineStatus.RUNNING);
        }
    }

    private void setImageTag(PipelineEntity pipeline) {
        if (config.pipelineBuilderImage().isEmpty()) {
            throw new IllegalStateException("Pipeline builder image is not configured");
        }
        AppPublishInfoDTO info = pipeline.getPublishInfo();
        String imageName = info == null ? null : info.getImageName();
        if (StringUtils.isEmpty(imageName)) {
            Log.infof("No image name to set for pipeline %d", pipeline.getId());
            return;
        }
        if (pipeline.getRunMode() == PipelineRunMode.SCAN_ONLY && info.getVulnerabilityScanResult() == null) {
            return;
        }
        if (pipeline.getPipelineType().equals(PipelineType.MODEL)) {
            ModelSubEntity modelSub = pipeline.getModelSub();
            if (modelSub == null) {
                Log.errorf("Pipeline %d has no model subscription associated", pipeline.getId());
                throw new NotFoundException("No model associated with this pipeline");
            }
            if (pipeline.getRunMode() == PipelineRunMode.SCAN_ONLY && !imageName.equals(modelSub.getImageName())) {
                return;
            }
            modelSub.setImageName(imageName);
            modelSub.setPublishInfo(info);
            Log.infof("Setting image name for model sub %d to %s", modelSub.getId(), imageName);
        } else {
            FederatedAppVersionEntity appVersion = pipeline.getAppVersion();
            if (appVersion == null) {
                Log.errorf("Pipeline %d has no app version associated", pipeline.getId());
                throw new NotFoundException("No app version associated with this pipeline");
            }
            if (pipeline.getRunMode() == PipelineRunMode.SCAN_ONLY && !imageName.equals(appVersion.getImageName())) {
                return;
            }
            appVersion.setImageName(imageName);
            appVersion.setPublishInfo(info);
            Log.infof("Setting image name for app version %d to %s", appVersion.getId(), imageName);
        }
    }

    public void userHasRights(PipelineEntity entity, String keycloakId) {
        if (entity.getPipelineType().equals(PipelineType.MODEL)) {
            if (entity.getModelSub() == null) {
                throw new NotFoundException("No model associated with this pipeline");
            }
            if (!modelAccessBO.hasUserAnyRightsBySubId(keycloakId, entity.getModelSub().getId())) {
                throw new ForbiddenException();
            }
        } else {
            if (entity.getAppVersion() == null) {
                throw new NotFoundException("No model associated with this pipeline");
            }
            if (!appAuthorBO.isUserAuthorVersionId(keycloakId, entity.getAppVersion().getId())) {
                throw new ForbiddenException();
            }
        }

    }

    public FederatedAppEntity getFederatedAppEntity(PipelineEntity entity) {
        FederatedAppEntity appEntity;
        if (entity.getPipelineType().equals(PipelineType.MODEL)) {
            if (entity.getModelSub() == null) {
                throw new NotFoundException("No model associated with this pipeline");
            }
            appEntity = entity.getModelSub().getModelVersion().getModel().getFederatedAppVersion().getFederatedApp();
        } else {
            if (entity.getAppVersion() == null) {
                throw new NotFoundException("No model associated with this pipeline");
            }
            appEntity = entity.getAppVersion().getFederatedApp();
        }
        return appEntity;
    }

    public PipelineDTO stopPipeline(Long id, String keycloakId) {
        PipelineDTO stopped = setPipelineStatusTransactional(id, keycloakId, PipelineStatus.STOPPED);
        toolApiKeyService.revoke(Scope.PIPELINE_RUN, id);

        boolean cleanupOk = true;
        try {
            if (StringUtils.isNotBlank(stopped.getContainerId())) {
                containerClient.cleanupWorkflow(stopped.getContainerId(), true);
            } else {
                Log.warnf("Pipeline %d has no containerId - marking as STOPPED without cleanup call", id);
            }
        } catch (Exception e) {
            cleanupOk = false;
            Log.error("Failed to stop pipeline container: " + e.getMessage(), e);
        }
        if (!cleanupOk) {
            return setPipelineStatusTransactional(id, keycloakId, PipelineStatus.FAILED);
        }
        return stopped;
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public PipelineDTO setPipelineStatusTransactional(Long id, String keycloakId, PipelineStatus status) {
        Optional<PipelineEntity> entityOptional = ao.findByIdOptional(id, LockModeType.PESSIMISTIC_WRITE);
        if (entityOptional.isEmpty()) {
            throw new NotFoundException(String.format("Property with the ID %s not found", id));
        }
        userHasRights(entityOptional.get(), keycloakId);

        PipelineEntity entity = entityOptional.get();
        entity.setPipelineStatus(status);
        updateRebuilding(entity);
        ao.persist(entity);
        PipelineDTO updated = mapper.entityToDto(entity);
        UPDATES.onNext(updated);
        return updated;
    }

    public Optional<byte[]> getFilesForPipeline(Long id, String secret) {
        Optional<PipelineEntity> entityOptional = ao.findByIdAndSecretOptional(id, secret, LockModeType.PESSIMISTIC_READ);
        if (entityOptional.isEmpty()) {
            throw new NotFoundException(String.format("Property with the ID %s not found", id));
        }
        PipelineEntity entity = entityOptional.get();
        if (entity.getPipelineType() != PipelineType.MODEL) {
            return Optional.empty();
        }
        ModelSubEntity subEntity = entity.getModelSub();
        if (subEntity == null) {
            throw new NotFoundException("No model associated with this pipeline");
        }
        return Optional.of(modelSubFileBO.getFilesForPipeline(subEntity));
    }


}
