package bio.cosy.feddb.local.api.notification;

import bio.cosy.feddb.local.api.auth.UserIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.core.Response;

import java.util.List;

@ApplicationScoped
public class NotificationServiceImpl implements NotificationService {

    @Inject
    NotificationBO notificationBO;

    @Inject
    UserIdentity userIdentity;

    @Override
    public List<NotificationDTO> listNotifications() {
        return notificationBO.findActiveForUser(userIdentity.getKeycloakId());
    }

    @Override
    public NotificationDTO getNotification(Long id) {
        return notificationBO.findByIdForUser(id, userIdentity.getKeycloakId());
    }

    @Override
    @Transactional
    public NotificationDTO createNotification(NotificationDTO notificationDTO) {
        return notificationBO.createForUser(notificationDTO, userIdentity.getKeycloakId());
    }

    @Override
    @Transactional
    public NotificationDTO updateNotification(Long id, NotificationDTO notificationDTO) {
        return notificationBO.updateForUser(id, notificationDTO, userIdentity.getKeycloakId());
    }

    @Override
    @Transactional
    public NotificationDTO markAsRead(Long id) {
        return notificationBO.markAsRead(id, userIdentity.getKeycloakId());
    }

    @Override
    @Transactional
    public NotificationDTO archiveNotification(Long id) {
        return notificationBO.archive(id, userIdentity.getKeycloakId());
    }

    @Override
    @Transactional
    public Response deleteNotification(Long id) {
        notificationBO.deleteForUser(id, userIdentity.getKeycloakId());
        return Response.ok().build();
    }
}
