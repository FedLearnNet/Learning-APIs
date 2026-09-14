package de.unihamburg.daibetes.api.app.version;

import bio.cosy.feddb.core.api.app.FederatedAppDTO;
import bio.cosy.feddb.core.api.app.FederatedAppDetailDTO;
import bio.cosy.feddb.core.api.app.FederatedAppVersionDTO;
import bio.cosy.feddb.core.api.app.PublishStatus;
import bio.cosy.feddb.core.base.BaseBo;
import de.unihamburg.daibetes.api.app.AppCreateDTO;
import de.unihamburg.daibetes.api.app.FederatedAppBO;
import de.unihamburg.daibetes.api.app.FederatedAppEntity;
import de.unihamburg.daibetes.api.app.audit.ToolAuditPendingDTO;
import de.unihamburg.daibetes.api.app.config.FederatedAppConfigBO;
import de.unihamburg.daibetes.api.auth.UserBO;
import de.unihamburg.daibetes.api.model.ModelBO;
import de.unihamburg.daibetes.config.FLNetConfig;
import de.unihamburg.daibetes.helper.HashUtil;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotFoundException;
import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.Set;

@ApplicationScoped
public class FederatedAppVersionBO extends BaseBo<FederatedAppVersionDTO, FederatedAppVersionEntity, FederatedAppVersionAO, FederatedAppVersionMapper> {

    @Inject
    UserBO userBO;

    @Inject
    FederatedAppConfigBO configBO;

    @Inject
    ModelBO modelBO;

    @Inject
    FederatedAppBO appBO;

    @Inject
    FLNetConfig config;

    public List<ToolAuditPendingDTO> listForAudit(String keycloakId) {
        Long auditMinAcceptanceAmount = config.auditMinAcceptanceAmount();
        return ao.listForAudit(keycloakId, auditMinAcceptanceAmount).stream()
                .map(appBO::mapVersionToDetail)
                .map(ToolAuditPendingDTO::new)
                .toList();
    }

    public List<FederatedAppVersionDTO> getAllForApp(Long appId) {
        return mapper.entitiesToDtos(ao.findAllByAppId(appId)).stream()
                .peek(dto -> dto.setAppConfig(configBO.findByAppVersionId(dto.getId())))
                .toList();
    }

    public FederatedAppVersionDTO getById(Long appVersionId) {
        FederatedAppVersionDTO dto = super.getById(appVersionId);
        dto.setAppConfig(configBO.findByAppVersionId(appVersionId));
        return dto;
    }


    public FederatedAppVersionEntity create(AppCreateDTO appDTO, Long appId) {
        FederatedAppVersionDTO versionDTO = new FederatedAppVersionDTO();
        versionDTO.setShortDescription(appDTO.getShortDescription());
        versionDTO.setLongDescription("");
        versionDTO.setAppVersion("0.0.0");
        versionDTO.setImageName(null);
        versionDTO.setFederatedAppId(appId);
        versionDTO.setVersionPublishStatus(PublishStatus.UNPUBLISHED);
        FederatedAppVersionEntity entity = mapper.dtoToEntity(versionDTO);
        ao.persist(entity);
        return entity;
    }


    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public FederatedAppVersionDTO publishTransactional(Long appVersionId, FederatedAppPublishDTO publishDTO) {
        FederatedAppVersionEntity existingVersionEntity = ao.findLastVersionByAppIdOptional(appVersionId)
                .orElseThrow(() -> new BadRequestException("No version found for app"));

        if (existingVersionEntity.getVersionPublishStatus().equalsName(PublishStatus.PUBLISHED)) {
            Log.errorf("Attempt to publish already published for app %d with version id %d", appVersionId, existingVersionEntity.getId());
            //throw new BadRequestException("Version already published");
        }
        if (StringUtils.isEmpty(existingVersionEntity.getImageName())) {
            Log.errorf("Attempt to publish version without image for app %d", appVersionId);
            throw new BadRequestException("Cannot publish version without image");
        }
        Log.infof("Try to publishing image %s for app %d with version id %d", existingVersionEntity.getImageName(),
                appVersionId,
                existingVersionEntity.getId());

        //version will be splitted into major, minor, patch inside each setter
        existingVersionEntity.setPatchVersion(publishDTO.getVersion());
        existingVersionEntity.setMajorVersion(publishDTO.getVersion());
        existingVersionEntity.setMinorVersion(publishDTO.getVersion());
        existingVersionEntity.setVersionPublishStatus(PublishStatus.PUBLISHED);
        existingVersionEntity.setChangelog(publishDTO.getChangelog());
        existingVersionEntity.setNeedsInternetAccess(publishDTO.getNeedsInternetAccess());
        existingVersionEntity.setPublishHash(HashUtil.sha256(mapper.entityToDto(existingVersionEntity)));
        ao.persist(existingVersionEntity);
        Log.infof("Published image %s for app %d with version id %d", existingVersionEntity.getImageName(), appVersionId, existingVersionEntity.getId());
        FederatedAppVersionDTO version = mapper.entityToDto(existingVersionEntity);
        if (publishDTO.getCreateModel()) {
            modelBO.createForPublish(version);
        }
        return createUnpublishedVersion(existingVersionEntity, publishDTO.getVersion());
    }

    public void createUnpublished(Long appVersionId) {
        FederatedAppVersionEntity existingVersionEntity = ao.findLastVersionByAppIdOptional(appVersionId)
                .orElseThrow(() -> new BadRequestException("No version found for app"));

        if (existingVersionEntity.getVersionPublishStatus().equalsName(PublishStatus.UNPUBLISHED)) {
            Log.warnf("Unpublished version already exists for app %d with version id %d, creating another unpublished version", appVersionId, existingVersionEntity.getId());
        }

        createUnpublishedVersion(existingVersionEntity, existingVersionEntity.getVersionFormatted());
    }

    public FederatedAppVersionDTO createUnpublishedVersion(FederatedAppVersionEntity existingVersionEntity, String version) {
        // detaching the existing version lead to issues with hibernate overwriting the
        // published version
        // this is why we instead now copy the data into a new version entity
        FederatedAppVersionEntity newVersionEntity = new FederatedAppVersionEntity();
        newVersionEntity.setFederatedApp(existingVersionEntity.getFederatedApp());
        newVersionEntity.setShortDescription(existingVersionEntity.getShortDescription());
        newVersionEntity.setLongDescription(existingVersionEntity.getLongDescription());
        newVersionEntity.setVersionPublishStatus(PublishStatus.UNPUBLISHED);
        newVersionEntity.setPublishHash(null);
        newVersionEntity.setImageName(null);
        newVersionEntity.setChangelog(null);
        newVersionEntity.setCertificationLevel(0);
        newVersionEntity.setPublishHash(null);
        setBaseValuesNull(newVersionEntity);
        //version will be splitted into major, minor, patch inside each setter
        newVersionEntity.setMajorVersion(version);
        newVersionEntity.setMinorVersion(version);
        newVersionEntity.setPatchVersion(version);
        //increase patch version for the new unpublished version
        newVersionEntity.increasePatchVersion();

        ao.persist(newVersionEntity);
        configBO.publish(existingVersionEntity, newVersionEntity);

        return mapper.entityToDto(newVersionEntity);
    }

    public FederatedAppVersionDTO update(FederatedAppDetailDTO appDTO) {
        FederatedAppVersionEntity lastVersion = ao.findLastVersionByAppId(appDTO.getId());
        if (lastVersion == null) {
            throw new NotFoundException("Cannot find latest Version for app " + appDTO.getId());
        }
        lastVersion.setShortDescription(appDTO.getShortDescription());
        lastVersion.setLongDescription(appDTO.getLongDescription());
        lastVersion.setPatchVersion(appDTO.getLatestVersion());
        lastVersion.setMajorVersion(appDTO.getLatestVersion());
        lastVersion.setMinorVersion(appDTO.getLatestVersion());
        ao.persist(lastVersion);
        ao.flush();
        return mapper.entityToDto(lastVersion);
    }

    public void deleteAllVersions(Set<FederatedAppVersionEntity> versions) {
        for (FederatedAppVersionEntity version : versions.stream().toList()) {
            if (version.hasImage()) {
                throw new BadRequestException("App having image cannot be deleted");
            }
            ao.deleteById(version.getId());
        }
    }

    public FederatedAppVersionDTO create(FederatedAppVersionDTO dto) {
        return super.create(dto);
    }

    public FederatedAppVersionDTO create(FederatedAppVersionDTO dto, FederatedAppEntity app) {
        return mapper.entityToDto(createEntity(dto, app));
    }

    public FederatedAppVersionEntity overrideEntity(FederatedAppVersionDTO dto, FederatedAppVersionEntity existingVersion) {
        mapper.updateEntityFromDto(dto, existingVersion);
        ao.persist(existingVersion);
        return existingVersion;
    }

    public FederatedAppVersionEntity createEntity(FederatedAppVersionDTO dto, FederatedAppEntity app) {
        setBaseValuesNull(dto);
        FederatedAppVersionEntity entity = mapper.dtoToEntity(dto);
        entity.setFederatedApp(app);
        ao.persist(entity);
        return entity;
    }


    public FederatedAppVersionDTO updateCertification(FederatedAppDTO data, String keycloakId) {
        if (data.getCertificationLevel() == null) {
            return null;
        }

        int certificationLevel = data.getCertificationLevel();
        if (userBO.isCertifier(keycloakId)) {
            if (certificationLevel == 0 || certificationLevel == 2) {
                FederatedAppVersionEntity lastVersion = ao.findLastVersionByAppId(data.getId());
                if (lastVersion != null && lastVersion.getCertificationLevel() != certificationLevel) {
                    lastVersion.setCertificationLevel(certificationLevel);
                    ao.persist(lastVersion);
                    return mapper.entityToDto(lastVersion);
                }
            }
            throw new ForbiddenException();
        }

        return null;
    }
}
