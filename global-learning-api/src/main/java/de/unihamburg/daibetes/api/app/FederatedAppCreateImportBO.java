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
import de.unihamburg.daibetes.config.FLNetConfig;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

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

    public void createExternal(FederatedAppDetailDTO external) {

        boolean createUnpublished = config.toolImports().createUnpublishedVersion();
        boolean overrideExisting = config.toolImports().overrideExisting();

        Log.debugf("Creating external app with name %s", external.getName());
        if (external.getUniqueAppId() == null) {
            Log.infof("External app %s has no unique app id, skipping", external.getName());
            return;
        }
        Optional<FederatedAppEntity> existingAppOptional = appAO.findByUniqueAppId(external.getUniqueAppId());

        if (external.getName() == null || external.getSlug() == null || external.getType() == null) {
            Log.infof("External app with unique id %s is missing required fields, skipping", external.getUniqueAppId());
            return;
        }
        if (existingAppOptional.isEmpty()) {
            existingAppOptional = appAO.findBySlug(external.getSlug());
            if (existingAppOptional.isPresent() && existingAppOptional.get().getUniqueAppId() != null) {
                Log.infof("External app with slug %s already exists, skipping", external.getSlug());
                return;
            }
        }
        appBO.setBaseValuesNull(external);
        FederatedAppEntity entity;
        // only a single version on both sides can be matched unambiguously, so only then values may be replaced
        boolean overrideSingleVersion = false;
        if (existingAppOptional.isEmpty()) {
            entity = mapper.dtoToEntity(external);
            appAO.persist(entity);
            for (FederatedAppAuthorDTO author : external.getAuthors()) {
                authorBO.create(entity, author.getKeycloakId());
            }
        } else {
            entity = existingAppOptional.get();
            if (entity.getAuthors() == null) {
                entity.setAuthors(Set.of());
            } else {
                Set<String> currentAuthorIds = entity.getAuthors().stream().map(FederatedAppAuthorEntity::getKeycloakId).collect(Collectors.toSet());
                for (FederatedAppAuthorDTO author : external.getAuthors()) {
                    if (currentAuthorIds.contains(author.getKeycloakId())) {
                        continue;
                    }
                    authorBO.create(entity, author.getKeycloakId());
                }
            }
            Log.infof("External app with unique id %s already exists, continue with %d", external.getUniqueAppId(), entity.getId());
            if (overrideExisting) {
                // app level values live on the app itself, so overriding a version does not reach them
                mapper.updateEntityFromDto(external, entity);
                appAO.persist(entity);
            }
            overrideSingleVersion = overrideExisting
                    && external.getVersions() != null && external.getVersions().size() == 1
                    && versionAO.findAllByAppId(entity.getId()).size() == 1;
        }
        //Save basic config for new app version
        boolean overrodeExistingVersion = false;
        for (FederatedAppVersionDTO version : external.getVersions()) {
            String appNameVersion = external.getName() + " (" + version.getVersion() + ")";
            if (version.getVersionPublishStatus().equals(PublishStatus.UNPUBLISHED)) {
                Log.infof("External app version %s with version publish status unpublished, skipping", appNameVersion);
                continue;
            }
            if (version.getPublishHash() == null) {
                Log.infof("External app version %s has no publish hash, skipping", appNameVersion);
                continue;
            }
            if (overrideSingleVersion) {
                try {
                    FederatedAppVersionEntity existingVersion = versionAO.findAllByAppId(entity.getId()).getFirst();
                    versionBO.overrideEntity(version, existingVersion);
                    configBO.setBaseValuesNull(version.getAppConfig());
                    configBO.persist(version.getAppConfig(), existingVersion.getId(), external.getType());
                    overrodeExistingVersion = true;
                    Log.infof("External app version %s overrode existing version with id %d", appNameVersion, existingVersion.getId());
                } catch (Exception e) {
                    Log.warnf("Failed to override external app version %s: %s", appNameVersion, e.getMessage());
                }
                continue;
            }
            if (versionAO.existsByPublishHash(version.getPublishHash(), external.getUniqueAppId())) {
                Log.infof("External app version %s with publish hash %s already exists, skipping", appNameVersion, version.getPublishHash());
                continue;
            }
            try {
                FederatedAppVersionEntity updatedVersion = versionBO.createEntity(version, entity);
                Log.infof("External app version %s created with id %d", appNameVersion, updatedVersion.getId());
                configBO.setBaseValuesNull(version.getAppConfig());
                configBO.create(version.getAppConfig(), updatedVersion);
            } catch (Exception e) {
                Log.warnf("Failed to create external app version %s: %s", appNameVersion, e.getMessage());
            }
        }
        // an additional unpublished version would make the app ambiguous for future overrides
        if (createUnpublished && !overrodeExistingVersion) {
            Log.infof("Creating unpublished version for external app %s", external.getName());
            try {
                versionBO.createUnpublished(entity.getId());
            } catch (Exception e) {
                Log.warnf("Failed to create unpublished version for external app %s: %s", external.getName(), e.getMessage());
            }
        }
        Log.infof("External app with name %s created successfully", external.getName());

    }
}
