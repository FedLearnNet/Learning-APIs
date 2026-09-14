package de.unihamburg.daibetes.api.notification;

import bio.cosy.feddb.core.api.notification.CreateNotificationDTO;
import bio.cosy.feddb.core.base.BaseBo;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;

import java.util.List;

@ApplicationScoped
public class NotificationBO extends BaseBo<NotificationDTO, NotificationEntity, NotificationAO, NotificationMapper> {

    public void fireAndSendForUser(CreateNotificationDTO dto, String keycloakId) {
        try {
            createAndSendForUser(dto, keycloakId);
        } catch (RuntimeException exception) {
            Log.errorf("Failed to send notification: %s", exception.getMessage());
        }
    }

    public NotificationDTO createAndSendForUser(CreateNotificationDTO dto, String keycloakId) {
        NotificationEntity entity = mapper.createEntityToEntity(dto);
        entity.setRecipientUserId(keycloakId);
        return mapper.entityToDto(ao.createTransactional(entity));
    }

    public List<NotificationDTO> findActiveForUser(String keycloakId) {
        return mapper.entitiesToDtos(ao.findActiveForUser(keycloakId));
    }

    public NotificationDTO findByIdForUser(Long id, String keycloakId) {
        return mapper.entityToDto(findEntityByIdForUser(id, keycloakId));
    }

    @Transactional
    public NotificationDTO createForUser(NotificationDTO dto, String keycloakId) {
        NotificationEntity entity = mapper.dtoToEntity(dto);
        entity.setRecipientUserId(keycloakId);
        return mapper.entityToDto(ao.createTransactional(entity));
    }

    @Transactional
    public NotificationDTO updateForUser(Long id, NotificationDTO dto, String keycloakId) {
        NotificationEntity existing = findEntityByIdForUser(id, keycloakId);
        existing.setTitle(dto.getTitle());
        existing.setMessage(dto.getMessage());
        existing.setType(dto.getType());
        existing.setPriority(dto.getPriority());
        existing.setSourceService(dto.getSourceService());
        existing.setSourceEntityType(dto.getSourceEntityType());
        existing.setSourceEntityId(dto.getSourceEntityId());
        existing.setActionUrl(dto.getActionUrl());
        existing.setStatus(dto.getStatus());
        return mapper.entityToDto(existing);
    }

    @Transactional
    public void deleteForUser(Long id, String keycloakId) {
        if (!ao.deleteByIdAndUser(id, keycloakId)) {
            throw new NotFoundException("Notification not found");
        }
    }

    @Transactional
    public NotificationDTO markAsRead(Long id, String keycloakId) {
        NotificationEntity entity = ao.markAsRead(id, keycloakId);
        if (entity == null) {
            throw new NotFoundException("Notification not found");
        }
        return mapper.entityToDto(entity);
    }

    @Transactional
    public NotificationDTO archive(Long id, String keycloakId) {
        NotificationEntity entity = ao.archive(id, keycloakId);
        if (entity == null) {
            throw new NotFoundException("Notification not found");
        }
        return mapper.entityToDto(entity);
    }

    private NotificationEntity findEntityByIdForUser(Long id, String keycloakId) {
        return ao.findByIdAndUser(id, keycloakId)
                .orElseThrow(() -> new NotFoundException("Notification not found"));
    }
}
