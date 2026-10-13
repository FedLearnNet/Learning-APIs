package de.unihamburg.daibetes.api.app;

import bio.cosy.feddb.core.api.app.FederatedAppAuthorDTO;
import bio.cosy.feddb.core.api.app.FederatedAppDetailDTO;
import bio.cosy.feddb.core.api.app.FederatedAppVersionDTO;
import bio.cosy.feddb.core.api.app.PublishStatus;
import de.unihamburg.daibetes.api.app.author.FederatedAppAuthorBO;
import de.unihamburg.daibetes.api.app.author.FederatedAppAuthorEntity;
import de.unihamburg.daibetes.api.app.config.FederatedAppConfigBO;
import de.unihamburg.daibetes.api.app.version.FederatedAppVersionAO;
import de.unihamburg.daibetes.api.app.version.FederatedAppVersionBO;
import de.unihamburg.daibetes.api.app.version.FederatedAppVersionEntity;
import de.unihamburg.daibetes.api.build.pipeline.ToolImportBuildBO;
import de.unihamburg.daibetes.config.FLNetConfig;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.apache.commons.lang3.StringUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@ApplicationScoped
public class FederatedAppCreateImportBO {

    @Inject
    FederatedAppAuthorBO authorBO;

    @Inject
    FederatedAppVersionBO versionBO;

    @Inject
    FederatedAppConfigBO configBO;

    @Inject
    FederatedAppVersionAO versionAO;

    @Inject
    FederatedAppBO appBO;

    @Inject
    FederatedAppAO appAO;

    @Inject
    FederatedAppMapper mapper;

    @Inject
    FLNetConfig config;

    @Inject
    ToolImportBuildBO buildBO;

    @Transactional
    public void createExternal(FederatedAppDetailDTO external) {
        if (!hasRequiredFields(external)) {
            return;
        }
        Log.debugf("Importing external app with name %s", external.getName());

        Optional<FederatedAppEntity> existingApp = appAO.findByUniqueAppId(external.getUniqueAppId())
                .or(() -> appAO.findBySlug(external.getSlug()));
        if (existingApp.isPresent() && existingApp.get().getUniqueAppId() != null
                && !external.getUniqueAppId().equals(existingApp.get().getUniqueAppId())) {
            Log.infof("External app with slug %s already exists, skipping", external.getSlug());
            return;
        }

        appBO.setBaseValuesNull(external);
        FederatedAppEntity entity = createOrUpdateApp(external, existingApp);
        importAuthors(external, entity);

        Optional<FederatedAppVersionEntity> versionToOverride = findVersionToOverride(external, entity, existingApp.isPresent());
        boolean versionOverridden = importVersions(external, entity, versionToOverride);
        if (!versionOverridden) {
            createUnpublishedVersionIfRequested(external, entity);
        }
        Log.infof("External app with name %s imported successfully", external.getName());
    }

    private boolean hasRequiredFields(FederatedAppDetailDTO external) {
        if (external == null) {
            Log.infof("External app is missing, skipping");
            return false;
        }
        if (external.getUniqueAppId() == null) {
            Log.infof("External app %s has no unique app id, skipping", external.getName());
            return false;
        }
        if (StringUtils.isBlank(external.getName()) || StringUtils.isBlank(external.getSlug()) || external.getType() == null) {
            Log.infof("External app with unique id %s is missing required fields, skipping", external.getUniqueAppId());
            return false;
        }
        return true;
    }

    private FederatedAppEntity createOrUpdateApp(FederatedAppDetailDTO external, Optional<FederatedAppEntity> existingApp) {
        if (existingApp.isEmpty()) {
            FederatedAppEntity entity = mapper.dtoToEntity(external);
            appAO.persist(entity);
            return entity;
        }

        FederatedAppEntity entity = existingApp.get();
        Log.infof("External app with unique id %s already exists, continue with %d", external.getUniqueAppId(), entity.getId());
        if (config.toolImports().overrideExisting()) {
            mapper.updateEntityFromDto(external, entity);
            appAO.persist(entity);
        }
        return entity;
    }

    private void importAuthors(FederatedAppDetailDTO external, FederatedAppEntity entity) {
        if (external.getAuthors() == null) {
            return;
        }
        Set<String> authorIds = entity.getAuthors() == null ? new HashSet<>() : entity.getAuthors().stream()
                .map(FederatedAppAuthorEntity::getKeycloakId)
                .collect(Collectors.toSet());
        for (FederatedAppAuthorDTO author : external.getAuthors()) {
            if (author == null || StringUtils.isBlank(author.getKeycloakId()) || !authorIds.add(author.getKeycloakId())) {
                continue;
            }
            authorBO.create(entity, author.getKeycloakId());
        }
    }

    private Optional<FederatedAppVersionEntity> findVersionToOverride(FederatedAppDetailDTO external, FederatedAppEntity entity,
                                                                     boolean appAlreadyExists) {
        if (!appAlreadyExists || !config.toolImports().overrideExisting()
                || external.getVersions() == null || external.getVersions().size() != 1) {
            return Optional.empty();
        }
        List<FederatedAppVersionEntity> versions = versionAO.findAllByAppId(entity.getId());

        if(versions.size() == 1){
            return Optional.of(versions.getFirst());
        }

        return Optional.empty();
    }

    private boolean importVersions(FederatedAppDetailDTO external, FederatedAppEntity entity,
                                   Optional<FederatedAppVersionEntity> versionToOverride) {
        if (external.getVersions() == null) {
            return false;
        }
        boolean versionOverridden = false;
        for (FederatedAppVersionDTO version : external.getVersions()) {
            if (version == null) {
                continue;
            }
            String appNameVersion = external.getName() + " (" + version.getAppVersion() + ")";
            if (version.getVersionPublishStatus() != PublishStatus.PUBLISHED) {
                Log.infof("External app version %s is not published, skipping", appNameVersion);
                continue;
            }
            if (StringUtils.isBlank(version.getPublishHash())) {
                Log.infof("External app version %s has no publish hash, skipping", appNameVersion);
                continue;
            }
            Optional<FederatedAppVersionEntity> existingVersion = versionAO.findByPublishHash(version.getPublishHash(), external.getUniqueAppId());
            if (versionToOverride.isPresent()) {
                versionOverridden = overrideVersion(version, versionToOverride.get(), external, appNameVersion);
                if (versionOverridden) {
                    buildBO.enqueue(versionToOverride.get(), true);
                }
                continue;
            }
            if (existingVersion.isPresent()) {
                Log.infof("External app version %s with publish hash %s already exists, skipping", appNameVersion, version.getPublishHash());
                buildBO.enqueue(existingVersion.get(), true);
                continue;
            }
            createVersion(version, entity, appNameVersion);
        }
        return versionOverridden;
    }

    private boolean overrideVersion(FederatedAppVersionDTO version, FederatedAppVersionEntity existingVersion,
                                    FederatedAppDetailDTO external, String appNameVersion) {
        if (existingVersion.isRebuilding()) {
            Log.infof("External app version %s is rebuilding, skipping override", appNameVersion);
            return false;
        }
        try {
            version.setRebuilding(false);
            versionBO.overrideEntity(version, existingVersion);
            configBO.setBaseValuesNull(version.getAppConfig());
            configBO.persist(version.getAppConfig(), existingVersion.getId(), external.getType());
            Log.infof("External app version %s overrode existing version with id %d", appNameVersion, existingVersion.getId());
            return true;
        } catch (Exception e) {
            Log.warnf(e, "Failed to override external app version %s", appNameVersion);
            return false;
        }
    }

    private void createVersion(FederatedAppVersionDTO version, FederatedAppEntity entity, String appNameVersion) {
        try {
            version.setRebuilding(false);
            FederatedAppVersionEntity createdVersion = versionBO.createEntity(version, entity);
            configBO.setBaseValuesNull(version.getAppConfig());
            configBO.create(version.getAppConfig(), createdVersion);
            buildBO.enqueue(createdVersion, false);
            Log.infof("External app version %s created with id %d", appNameVersion, createdVersion.getId());
        } catch (Exception e) {
            Log.warnf(e, "Failed to create external app version %s", appNameVersion);
        }
    }

    private void createUnpublishedVersionIfRequested(FederatedAppDetailDTO external, FederatedAppEntity entity) {
        if (!config.toolImports().createUnpublishedVersion()) {
            return;
        }
        Optional<FederatedAppVersionEntity> latestVersion = versionAO.findLastVersionByAppIdOptional(entity.getId());
        if (latestVersion.isEmpty() || latestVersion.get().isRebuilding()
                || latestVersion.get().getVersionPublishStatus() == PublishStatus.UNPUBLISHED) {
            return;
        }
        try {
            Log.infof("Creating unpublished version for external app %s", external.getName());
            versionBO.createUnpublished(entity.getId());
        } catch (Exception e) {
            Log.warnf(e, "Failed to create unpublished version for external app %s", external.getName());
        }
    }
}
