package de.unihamburg.daibetes.api.build.pipeline;

import bio.cosy.feddb.core.api.app.AppPublishInfoDTO;
import bio.cosy.feddb.core.api.pipeline.PipelineStatus;
import bio.cosy.feddb.core.base.BaseBo;
import bio.cosy.feddb.core.services.orch.dto.CreateContainerResponseDTO;
import bio.cosy.feddb.core.services.orch.dto.StartPipelineDTO;
import de.unihamburg.daibetes.api.app.FederatedAppEntity;
import de.unihamburg.daibetes.api.app.author.FederatedAppAuthorBO;
import de.unihamburg.daibetes.api.app.version.FederatedAppVersionAO;
import de.unihamburg.daibetes.api.app.version.FederatedAppVersionEntity;
import de.unihamburg.daibetes.api.build.pipeline.steps.PipelineStepBO;
import de.unihamburg.daibetes.api.build.pipeline.steps.StepName;
import de.unihamburg.daibetes.api.model.access.ModelAccessBO;
import de.unihamburg.daibetes.api.model.sub.ModelSubAO;
import de.unihamburg.daibetes.api.model.sub.ModelSubEntity;
import de.unihamburg.daibetes.api.model.sub.file.ModelSubFileBO;
import de.unihamburg.daibetes.config.FLNetConfig;
import de.unihamburg.daibetes.services.KeycloakService;
import de.unihamburg.daibetes.services.OrchContainerServiceClient;
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
    OrchContainerServiceClient containerClient;

    @Inject
    KeycloakService keycloakService;

    @Inject
    PipelineStepBO stepBO;

    @Inject
    FederatedAppAuthorBO appAuthorBO;

    @Inject
    ModelAccessBO modelAccessBO;

    @Inject
    ModelSubAO modelSubAO;

    @Inject
    ModelSubFileBO modelSubFileBO;

    @Inject
    FederatedAppVersionAO appVersionAO;

    @Inject
    FLNetConfig config;

    @ConfigProperty(name = "quarkus.http.port")
    protected int serverPort;

    public PipelineDTO create(PipelineCreateDTO dto, String keycloakId) {
        PipelineDTO pipeline = new PipelineDTO();
        pipeline.setPipelineType(dto.getPipelineType());
        pipeline.setPipelineStatus(PipelineStatus.PENDING);
        pipeline.setSecret(RandomStringUtils.secureStrong().nextAlphabetic(32));
        pipeline.setDockerTag(RandomStringUtils.secureStrong().nextAlphabetic(8));
        if (dto.getPipelineType().equals(PipelineType.MODEL)) {
            if (dto.getModelSubId() == null) {
                throw new BadRequestException("Model subscription ID must be provided for model pipelines");
            }
            if (!modelAccessBO.hasUserAnyRightsBySubId(keycloakId, dto.getModelSubId())) {
                throw new ForbiddenException();
            }
            pipeline.setModelSubId(dto.getModelSubId());
        } else {
            if (dto.getAppVersionId() == null) {
                throw new BadRequestException("App version ID must be provided for app pipelines");
            }
            if (!appAuthorBO.isUserAuthorVersionId(keycloakId, dto.getAppVersionId())) {
                throw new ForbiddenException();
            }
            pipeline.setAppVersionId(dto.getAppVersionId());
        }
        PipelineDTO created = create(pipeline);
        PipelineDTO steps = stepBO.createSteps(created);
        if (dto.getAutoStart()) {
            return start(steps.getId(), keycloakId);
        }
        return steps;
    }

    public PipelineDTO start(Long id, String keycloakId) {
        if (config.pipelineBuilderImage().isEmpty()) {
            throw new IllegalStateException("Pipeline builder image is not configured");
        }
        String builderImage = config.pipelineBuilderImage().get();
        Optional<PipelineEntity> entityOptional = ao.findByIdOptional(id);
        if (entityOptional.isEmpty()) {
            throw new NotFoundException(String.format("Property with the ID %s not found", id));
        }
        userHasRights(entityOptional.get(), keycloakId);
        PipelineEntity entity = entityOptional.get();
        String token = keycloakService.getServiceAccountToken();
        StartPipelineDTO startApp = new StartPipelineDTO();
        startApp.setPipelineId(id);
        startApp.setAppImage(builderImage);
        startApp.addEnvironment(token, id, entity.getSecret());
        Log.info("Starting container for pipeline " + id);
        CreateContainerResponseDTO resp;
        try {
            resp = containerClient.startPipeline(startApp, true, "", serverPort);
        } catch (Exception e) {
            String statusCode = e.getMessage() != null ? e.getMessage().split(" ")[0] : "Unknown";
            Log.error("Failed to start container (Status code: " + statusCode + "): " + e.getMessage(), e);
            throw new IllegalStateException("Failed to start container: " + e.getMessage(), e);
        }
        String containerId = resp.getId();
        if (containerId == null) {
            throw new IllegalStateException("Failed to start container for pipeline " + id);
        }
        entity.setContainerId(containerId);
        ao.persist(entity);
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
        dto.setDockerTag(entity.getDockerTag());
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
        entity.setPublishInfo(dto);
        ao.persist(entity);
        PipelineDTO updated = mapper.entityToDto(entity);
        UPDATES.onNext(updated);
        return updated;
    }


    private void updateOverallPipelineStatus(PipelineEntity pipeline) {
        boolean hasFailedStep = pipeline.getSteps().stream()
                .anyMatch(s -> s.getStepStatus() == PipelineStatus.FAILED);

        if (hasFailedStep) {
            pipeline.setPipelineStatus(PipelineStatus.FAILED);
            return;
        }
        boolean allStepsSucceeded = pipeline.getSteps().stream()
                .filter(f -> !f.getName().equals(StepName.PREFLIGHT))
                .allMatch(s -> s.getStepStatus() == PipelineStatus.SUCCESS || s.getStepStatus() == PipelineStatus.WARNING);

        if (allStepsSucceeded && pipeline.getSteps().size() >= 8) {
            pipeline.setPipelineStatus(PipelineStatus.SUCCESS);
        } else {
            pipeline.setPipelineStatus(PipelineStatus.RUNNING);
        }
        pipeline.setUpdatedAt(new Date());
        ao.persist(pipeline);
        //Set image tag if all steps succeeded and there are at least 5 steps and the pipeline is successfully persisted
        if (allStepsSucceeded && pipeline.getSteps().size() >= 8) {
            setImageTagTransactional(pipeline);
        }
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void setImageTagTransactional(PipelineEntity pipeline) {
        if (config.pipelineBuilderImage().isEmpty()) {
            throw new IllegalStateException("Pipeline builder image is not configured");
        }
        AppPublishInfoDTO info = pipeline.getPublishInfo();
        String imageName = info.getImageName();
        if (StringUtils.isEmpty(imageName)) {
            Log.infof("No image name to set for pipeline %d", pipeline.getId());
            return;
        }
        if (pipeline.getPipelineType().equals(PipelineType.MODEL)) {
            Log.infof("Try to persist image name for model sub %d to %s", pipeline.getModelSub().getId(), imageName);
            if (pipeline.getModelSub() == null) {
                Log.errorf("Pipeline %d has no model subscription associated", pipeline.getId());
                throw new NotFoundException("No model associated with this pipeline");
            }
            ModelSubEntity modelSub = modelSubAO.findById(pipeline.getModelSub().getId());
            if (modelSub.getImageName() != null && modelSub.getImageName().equals(imageName)) {
                return;
            }
            modelSub.setImageName(imageName);
            modelSub.setPublishInfo(info);
            modelSubAO.persist(modelSub);
            Log.infof("Setting image name for model sub %d to %s", modelSub.getId(), modelSub.getImageName());
        } else {
            Log.infof("Try to persist image name for app version %d to %s", pipeline.getAppVersion().getId(), imageName);
            if (pipeline.getAppVersion() == null) {
                Log.errorf("Pipeline %d has no app version associated", pipeline.getId());
                throw new NotFoundException("No model associated with this pipeline");
            }
            FederatedAppVersionEntity appVersion = appVersionAO.findById(pipeline.getAppVersion().getId());
            if (appVersion.getImageName() != null && appVersion.getImageName().equals(imageName)) {
                return;
            }
            appVersion.setImageName(imageName);
            appVersion.setPublishInfo(info);
            appVersionAO.persist(appVersion);
            Log.infof("Setting image name for app version %d to %s", appVersion.getId(), appVersion.getImageName());
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
        ao.persist(entity);
        PipelineDTO updated = mapper.entityToDto(entity);
        UPDATES.onNext(updated);
        return updated;
    }

    public byte[] getFilesForPipeline(Long id, String secret) {
        Optional<PipelineEntity> entityOptional = ao.findByIdAndSecretOptional(id, secret, LockModeType.PESSIMISTIC_READ);
        if (entityOptional.isEmpty()) {
            throw new NotFoundException(String.format("Property with the ID %s not found", id));
        }
        PipelineEntity entity = entityOptional.get();
        ModelSubEntity subEntity = entity.getModelSub();
        if (subEntity == null) {
            throw new NotFoundException("No model associated with this pipeline");
        }
        return modelSubFileBO.getFilesForPipeline(subEntity);
    }


}
