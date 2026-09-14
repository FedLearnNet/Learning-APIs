package de.unihamburg.daibetes.api.app.audit;

import bio.cosy.feddb.core.api.app.AuditDecision;
import bio.cosy.feddb.core.api.app.FederatedAppDetailDTO;
import bio.cosy.feddb.core.api.app.ToolAuditDTO;
import bio.cosy.feddb.core.base.BaseBo;
import de.unihamburg.daibetes.api.app.FederatedAppBO;
import de.unihamburg.daibetes.api.app.version.FederatedAppVersionAO;
import de.unihamburg.daibetes.api.app.version.FederatedAppVersionEntity;
import de.unihamburg.daibetes.config.FLNetConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@ApplicationScoped
public class ToolAuditBO extends BaseBo<ToolAuditDTO, ToolAuditEntity, ToolAuditAO, ToolAuditMapper> {

    @Inject
    FederatedAppBO federatedAppBO;

    @Inject
    FLNetConfig config;

    @Inject
    FederatedAppVersionAO federatedAppVersionAO;

    public List<ToolAuditDTO> list(Long toolId, String filterKeycloakId) {
        if (filterKeycloakId != null && toolId != null) {
            return mapper.entitiesToDtos(ao.list(toolId, filterKeycloakId));
        }
        if (filterKeycloakId != null) {
            return mapper.entitiesToDtos(ao.list(filterKeycloakId));
        }
        if (toolId != null) {
            return mapper.entitiesToDtos(ao.list(toolId));
        }
        return mapper.entitiesToDtos(ao.listAll());
    }

    public ToolAuditDTO create(ToolAuditDTO dto, String keycloakId) {
        dto.setKeycloakId(keycloakId);
        ToolAuditEntity entity = mapper.dtoToEntity(dto);
        ao.persist(entity);
        updateCertificationLevel(entity);
        return mapper.entityToDto(entity);
    }

    public ToolAuditDTO update(Long id, ToolAuditDTO dto, String keycloakId) {
        Optional<ToolAuditEntity> entityOpt = ao.getByIdAndKeycloakId(id, keycloakId);
        if (entityOpt.isEmpty()) {
            throw new NotFoundException("ToolAudit with id " + id + " not found for user");
        }
        ToolAuditEntity entity = entityOpt.get();
        entity.setReason(dto.getReason());
        entity.setDecision(entity.getDecision());
        ao.persist(entity);
        updateCertificationLevel(entity);
        return mapper.entityToDto(entity);
    }

    public void delete(Long id, String keycloakId) {
        Optional<ToolAuditEntity> entityOpt = ao.getByIdAndKeycloakId(id, keycloakId);
        if (entityOpt.isEmpty()) {
            return;
        }
        ToolAuditEntity entity = entityOpt.get();
        ao.delete(entity);
        updateCertificationLevel(entity);
    }

    public ToolAuditCombinationDTO getByVersion(Long id, String keycloakId) {
        Optional<ToolAuditEntity> entityOpt = ao.getByIdAndKeycloakId(id, keycloakId);
        FederatedAppDetailDTO detail = federatedAppBO.getByVersionId(id);
        ToolAuditCombinationDTO combinationDTO = new ToolAuditCombinationDTO();
        combinationDTO.setAppDetail(detail);
        if (entityOpt.isEmpty()) {
            return combinationDTO;
        }
        combinationDTO.setAudit(mapper.entityToDto(entityOpt.get()));
        return combinationDTO;
    }

    public void updateCertificationLevel(ToolAuditEntity entity) {
        FederatedAppVersionEntity versionEntity = federatedAppVersionAO.findById(entity.getAppVersion().getId());
        if(versionEntity == null) {
            return;
        }
        long accepted = Optional.ofNullable(versionEntity
                        .getAudits()).orElseGet(() -> Set.of(entity))
                .stream().filter(a -> a.getDecision().equals(AuditDecision.ACCEPT))
                .count();
        long currentLevel = versionEntity.getCertificationLevel();
        if (accepted >= config.auditMinAcceptanceAmount() && currentLevel < 1) {
            if(accepted >= 5){
                accepted = 5;
            }
            versionEntity.setCertificationLevel((int) accepted);
            federatedAppVersionAO.persist(versionEntity);
        } else if (currentLevel != 0) {
            versionEntity.setCertificationLevel(0);
            federatedAppVersionAO.persist(versionEntity);
        }
    }
}
