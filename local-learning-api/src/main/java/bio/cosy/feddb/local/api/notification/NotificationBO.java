package bio.cosy.feddb.local.api.notification;

import bio.cosy.feddb.core.api.notification.CreateNotificationDTO;
import bio.cosy.feddb.core.base.BaseBo;
import bio.cosy.feddb.local.api.cohort.member.CohortMemberBO;
import bio.cosy.feddb.local.api.notification.provider.EmailDeliveryProvider;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@ApplicationScoped
public class NotificationBO extends BaseBo<NotificationDTO, NotificationEntity, NotificationAO, NotificationMapper> {

    @Inject
    EmailDeliveryProvider emailDeliveryProvider;

    @Inject
    CohortMemberBO cohortMemberBO;

    public void fireAndSendForCohort(CreateNotificationDTO dto, Long cohortId) {
        try {
            createAndSendForCohort(dto, cohortId);
        } catch (RuntimeException exception) {
            Log.errorf("Failed to send notification: %s", exception.getMessage());
        }
    }

    public void fireAndSendForCohorts(CreateNotificationDTO dto, Set<Long> cohortIds) {
        try {
            createAndSendForCohorts(dto, cohortIds);
        } catch (RuntimeException exception) {
            Log.errorf("Failed to send notification: %s", exception.getMessage());
        }
    }

    public List<NotificationDTO> createAndSendForCohort(CreateNotificationDTO dto, Long cohortId) {
        List<String> keycloakIds = cohortMemberBO.getAllKeycloakIdsForCohort(cohortId);
        return keycloakIds.stream()
                .map(keycloakId -> createAndSendForUser(dto, keycloakId))
                .toList();
    }

    public List<NotificationDTO> createAndSendForCohorts(CreateNotificationDTO dto, Set<Long> cohortIds) {
        return cohortIds.stream()
                .flatMap(cohortId -> cohortMemberBO.getAllKeycloakIdsForCohort(cohortId).stream())
                .collect(Collectors.toSet())
                .stream()
                .map(keycloakId -> createAndSendForUser(dto, keycloakId))
                .toList();
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

    public NotificationDTO createAndSendForUser(CreateNotificationDTO dto, String keycloakId) {
        NotificationEntity entity = mapper.createEntityToEntity(dto);
        entity.setRecipientUserId(keycloakId);
        NotificationDTO notification = mapper.entityToDto(ao.createTransactional(entity));
        emailDeliveryProvider.deliver(notification);
        return notification;
    }

    private NotificationEntity findEntityByIdForUser(Long id, String keycloakId) {
        return ao.findByIdAndUser(id, keycloakId)
                .orElseThrow(() -> new NotFoundException("Notification not found"));
    }
}
