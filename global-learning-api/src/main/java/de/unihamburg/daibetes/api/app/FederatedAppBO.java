package de.unihamburg.daibetes.api.app;

import bio.cosy.feddb.core.api.app.*;
import bio.cosy.feddb.core.api.app.config.ToolConfigsDTO;
import bio.cosy.feddb.core.api.store.StoreDTO;
import bio.cosy.feddb.core.base.BaseBo;
import de.unihamburg.daibetes.agent.store.StoreIngestor;
import de.unihamburg.daibetes.api.app.author.FederatedAppAuthorAO;
import de.unihamburg.daibetes.api.app.author.FederatedAppAuthorBO;
import de.unihamburg.daibetes.api.app.author.FederatedAppAuthorEntity;
import de.unihamburg.daibetes.api.app.config.FederatedAppConfigBO;
import de.unihamburg.daibetes.api.app.tag.FederatedAppTagBO;
import de.unihamburg.daibetes.api.app.version.FederatedAppPublishDTO;
import de.unihamburg.daibetes.api.app.version.FederatedAppVersionAO;
import de.unihamburg.daibetes.api.app.version.FederatedAppVersionBO;
import de.unihamburg.daibetes.api.app.version.FederatedAppVersionEntity;
import de.unihamburg.daibetes.dto.URLDTO;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotFoundException;
import org.apache.commons.lang3.StringUtils;

import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

@ApplicationScoped
public class FederatedAppBO extends BaseBo<FederatedAppDTO, FederatedAppEntity, FederatedAppAO, FederatedAppMapper> {

    @Inject
    FederatedAppAuthorBO authorBO;

    @Inject
    FederatedAppAuthorAO authorAO;

    @Inject
    FederatedAppVersionBO versionBO;

    @Inject
    FederatedAppTagBO tagBO;

    @Inject
    FederatedAppConfigBO configBO;

    @Inject
    FederatedAppVersionAO versionAO;

    @Inject
    StoreIngestor storeIngestor;

    @Inject
    ToolExternalHandler toolExternalHandler;

    public FederatedAppDetailDTO getById(Object idOrSlug) {
        return getById(ao.findApp(idOrSlug));
    }

    public FederatedAppDetailDTO getById(Long id) {
        return getById(ao.findByIdOptional(id));
    }

    public FederatedAppDetailDTO getById(Optional<FederatedAppEntity> appEntityOptional) {
        if (appEntityOptional.isEmpty()) {
            throw new NotFoundException("App not found");
        }
        FederatedAppDTO baseDto = mapper.entityToDto(appEntityOptional.get());
        return dtoToDetail(baseDto);
    }

    public FederatedAppDetailDTO getMyAppById(Long id, String keycloakId) {
        if (!authorBO.isUserAuthor(keycloakId, id)) {
            throw new ForbiddenException();
        }
        return getMyAppById(id);
    }

    public URLDTO saveToolAsJson(Long id, String keycloakId) {
        FederatedAppDetailDTO app = getMyAppById(id, keycloakId);
        Optional<Path> path = toolExternalHandler.saveToolAsJson(app);
        if (path.isEmpty()) {
            throw new NotFoundException("Failed to save tool as json");
        }
        return new URLDTO(path.get().toUri().toString());
    }

    public URLDTO replaceBuildInfoInJson(Long id, String keycloakId) {
        FederatedAppDetailDTO app = getMyAppById(id, keycloakId);
        return new URLDTO(toolExternalHandler.replaceBuildInfoInJson(app)
                .orElseThrow(() -> new NotFoundException("Failed to replace build information in tool JSON"))
                .toUri().toString());
    }

    public FederatedAppDetailDTO getMyAppById(Long id) {
        Optional<FederatedAppEntity> appEntityOptional = ao.findByIdOptional(id);
        if (appEntityOptional.isEmpty()) {
            throw new NotFoundException();
        }
        FederatedAppDTO baseDto = mapper.entityToDto(appEntityOptional.get());
        baseDto.setLatestVersionId(baseDto.getLatestUnpublishedVersionId());
        FederatedAppDetailDTO detailDto = dtoToDetail(baseDto);
        mapper.updateVersion(detailDto, appEntityOptional.get().getVersions());
        return detailDto;
    }


    public FederatedAppVersionDTO getMyAppVersionById(Long id, Long appVersionId, String keycloakId) {
        if (!authorBO.isUserAuthor(keycloakId, id)) {
            throw new ForbiddenException();
        }
        Optional<FederatedAppEntity> appEntityOptional = ao.findByIdOptional(id);
        if (appEntityOptional.isEmpty()) {
            throw new NotFoundException();
        }
        return this.versionBO.getById(appVersionId);
    }


    public FederatedAppDetailDTO dtoToDetail(FederatedAppDTO baseDto) {
        FederatedAppDetailDTO dto = mapper.dtoToDetailDto(baseDto);
        return dtoToDetail(dto);
    }

    public FederatedAppDetailDTO dtoToDetail(FederatedAppDetailDTO dto) {
        if (dto == null) {
            return null;
        }
        dto.setAppConfig(configBO.findByAppVersionId(dto.getLatestVersionId()));

        dto.setAuthors(authorBO.getAllForApp(dto.getId()));
        dto.setVersions(versionBO.getAllForApp(dto.getId()));
        return dto;
    }


    public FederatedAppDetailDTO getByVersionId(Long versionId, String keycloakId) {
        FederatedAppVersionDTO version = versionBO.getById(versionId);
        if (version == null) {
            throw new NotFoundException("App version not found");
        }
        FederatedAppDetailDTO app = getById(version.getFederatedAppId());

        if (!PublishStatus.PUBLISHED.equalsName(app.getPublishStatus())) {
            if (!authorBO.isUserAuthor(keycloakId, app.getId())) {
                throw new ForbiddenException();
            }
        }
        app.setVersions(List.of(version));
        app.setLatestVersionId(version.getId());
        return app;
    }

    public FederatedAppDetailDTO getByVersionId(Long versionId) {
        FederatedAppVersionDTO version = versionBO.getById(versionId);
        if (version == null) {
            throw new NotFoundException("App version not found");
        }
        FederatedAppDetailDTO app = getById(version.getFederatedAppId());
        app.setVersions(List.of(version));
        app.setLatestVersionId(version.getId());
        return app;
    }

    public FederatedAppDetailDTO getAppObject(Object idOrSlug, String keycloakId) {
        FederatedAppDetailDTO app = getById(idOrSlug);

        if (!PublishStatus.PUBLISHED.equalsName(app.getPublishStatus())) {
            if (!authorBO.isUserAuthor(keycloakId, app.getId())) {
                throw new ForbiddenException();
            }
        }

        return app;
    }

    public List<FederatedAppDTO> getAllForStore(String keycloakId) {
        List<FederatedAppEntity> entities = ao.getAllPublished();
        Set<FederatedAppEntity> apps = new HashSet<>(entities);
        if (StringUtils.isNotEmpty(keycloakId)) {
            List<FederatedAppAuthorEntity> appAuthors = authorAO.getAllForUser(keycloakId);
            apps.addAll(appAuthors.stream()
                    .map(FederatedAppAuthorEntity::getFederatedApp)
                    .collect(Collectors.toSet()));
        }
        return mapper.entitiesToDtos(apps.stream().sorted(Comparator.comparing(FederatedAppEntity::getId)).collect(Collectors.toList()));
    }

    public List<FederatedAppDetailDTO> getAllDetailedForStore(String keycloakId) {
        List<FederatedAppEntity> entities = ao.getAllPublished();
        Set<FederatedAppEntity> apps = new HashSet<>(entities);
        if (StringUtils.isNotEmpty(keycloakId)) {
            List<FederatedAppAuthorEntity> appAuthors = authorAO.getAllForUser(keycloakId);
            apps.addAll(appAuthors.stream()
                    .map(FederatedAppAuthorEntity::getFederatedApp)
                    .collect(Collectors.toSet()));
        }
        return apps.stream()
                .map(mapper::entityToDto)
                .map(this::dtoToDetail)
                .sorted(Comparator.comparing(FederatedAppDetailDTO::getId))
                .collect(Collectors.toList());
    }


    public List<FederatedAppDTO> getAll() {
        List<FederatedAppEntity> entities = ao.getAllPublished();
        return mapper.entitiesToDtos(entities);
    }

    public List<FederatedAppDetailDTO> listMyApps(String keycloakId) {
        List<FederatedAppAuthorEntity> appAuthors = authorAO.getAllForUser(keycloakId);
        return appAuthors.stream()
                .map(FederatedAppAuthorEntity::getFederatedApp)
                .distinct()
                .map(mapper::entityToDto)
                .map(this::dtoToDetail)
                .sorted(Comparator.comparing(FederatedAppDetailDTO::getId))
                .collect(Collectors.toList());
    }

    public FederatedAppDetailDTO create(AppCreateDTO data, String keycloakId) {

        // Save data (assuming there is a create method in the base class)
        FederatedAppDTO newApp = mapper.createDtoToDto(data);
        FederatedAppEntity entity = mapper.dtoToEntity(newApp);
        ao.persist(entity);
        Long newAppId = entity.getId();
        FederatedAppVersionEntity updatedVersionEntity = versionBO.create(data, newAppId);

        // Save author
        authorBO.create(newAppId, keycloakId);

        //Save basic config for new app version
        ao.flush();
        ToolConfigsDTO initialConfig = configBO.create(updatedVersionEntity, entity.getType());
        FederatedAppVersionDTO updatedVersion = versionBO.entityToDto(updatedVersionEntity);
        updatedVersion.setAppConfig(initialConfig);

        FederatedAppDTO baseDto = mapper.entityToDto(entity);

        FederatedAppDetailDTO newDto = dtoToDetail(baseDto);
        mapper.setAppVersion(newDto, updatedVersion);
        return newDto;
    }

    public FederatedAppDetailDTO publish(Long appId, String keycloakId, FederatedAppPublishDTO publishDTO) {
        if (!authorBO.isUserAuthor(keycloakId, appId)) {
            throw new ForbiddenException();
        }
        FederatedAppDetailDTO detail = getById(appId);
        FederatedAppVersionDTO newVersion = this.versionBO.publishTransactional(appId, publishDTO);
        mapper.setAppVersion(detail, newVersion);

        storeIngestor.ingestAsync(new StoreDTO(detail));
        return detail;
    }

    public FederatedAppDetailDTO update(FederatedAppDetailDTO data) {
        if (data.getIcon() != null) {
            //data.setIcon(decodeBase64File(data.getIcon()));
        }
        if (data.getTags() == null) {
            data.setTags(new HashSet<>());
        }
        Set<FederatedAppTagDTO> tags = new HashSet<>(data.getTags());

        // always update latest version
        FederatedAppVersionDTO updatedVersion = versionBO.update(data);

        ToolConfigsDTO config = configBO.findByAppVersionId(data.getId());
        if (config == null || !config.isEqualTo(data.getAppConfig())) {
            configBO.persist(data.getAppConfig(), updatedVersion.getId(), data.getType());
        } else {
            Log.infof("Tool config for app %d version %d has not changed, skipping config update", data.getId(), updatedVersion.getId());
        }
        FederatedAppEntity entity = updateEntity(data);

        tagBO.createForApp(tags, entity);

        FederatedAppDTO baseDto = mapper.entityToDto(entity);
        baseDto.setLatestVersionId(updatedVersion.getId());
        FederatedAppDetailDTO updatedDto = dtoToDetail(baseDto);
        mapper.setAppVersion(updatedDto, updatedVersion);
        return updatedDto;
    }

    public FederatedAppDetailDTO update(Object idOrSlug, FederatedAppDetailDTO data, String keycloakId) {
        FederatedAppDTO currentData = getById(idOrSlug);
        if (currentData == null) {
            throw new NotFoundException();
        }

        versionBO.updateCertification(data, keycloakId);

        if (!authorBO.isUserAuthor(keycloakId, data.getId())) {
            throw new ForbiddenException();
        }

        return update(data);
    }

    public void delete(Object idOrSlug, String keycloakId) {
        Optional<FederatedAppEntity> appEntityOptional = ao.findApp(idOrSlug);
        if (appEntityOptional.isEmpty()) {
            throw new NotFoundException();
        }

        FederatedAppEntity app = appEntityOptional.get();

        if (!authorBO.isUserAuthor(keycloakId, app.getId())) {
            throw new ForbiddenException();
        }

        versionBO.deleteAllVersions(app.getVersions());

        // due to cascade setup of models this will delete AppAuthor, AppRating records also
        deleteById(app.getId());
    }


    public void checkExistsById(Long id) {
        if (!ao.existsById(id)) {
            throw new NotFoundException("App not found");
        }
    }

    public void checkUserAccess(FederatedAppAuthCheckDTO appAuthCheckDTO, String keycloakId) {
        Optional<FederatedAppEntity> appOptional = ao.findByImageName(appAuthCheckDTO.getImageName());
        if (appOptional.isEmpty()) {
            throw new NotFoundException();
        }
        if (!authorBO.isUserAuthor(keycloakId, appOptional.get().getId())) {
            throw new ForbiddenException();
        }
    }

    public void createExternal(FederatedAppDetailDTO external, boolean createUnpublished, boolean overrideExisting) {
        Log.debugf("Creating external app with name %s", external.getName());
        if (external.getUniqueAppId() == null) {
            Log.infof("External app %s has no unique app id, skipping", external.getName());
            return;
        }
        Optional<FederatedAppEntity> existingAppOptional = ao.findByUniqueAppId(external.getUniqueAppId());

        if (external.getName() == null || external.getSlug() == null || external.getType() == null) {
            Log.infof("External app with unique id %s is missing required fields, skipping", external.getUniqueAppId());
            return;
        }
        if (existingAppOptional.isEmpty()) {
            existingAppOptional = ao.findBySlug(external.getSlug());
            if (existingAppOptional.isPresent() && existingAppOptional.get().getUniqueAppId() != null) {
                Log.infof("External app with slug %s already exists, skipping", external.getSlug());
                return;
            }
        }
        setBaseValuesNull(external);
        FederatedAppEntity entity;
        // only a single version on both sides can be matched unambiguously, so only then values may be replaced
        boolean overrideSingleVersion = false;
        if (existingAppOptional.isEmpty()) {
            entity = mapper.dtoToEntity(external);
            ao.persist(entity);
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
                ao.persist(entity);
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

    public FederatedAppDetailDTO mapVersionToDetail(FederatedAppVersionEntity versionEntity) {
        FederatedAppVersionDTO versionDTO = versionBO.entityToDto(versionEntity);
        FederatedAppDTO app = mapper.entityToDto(versionEntity.getFederatedApp());
        FederatedAppDetailDTO detail = mapper.dtoToDetailDto(app);

        detail.setLatestVersionId(versionEntity.getId());
        detail.setVersions(List.of(versionDTO));
        return detail;
    }
}
