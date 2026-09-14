package bio.cosy.feddb.core.api.notification;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.transaction.Transactional;

import java.util.Date;

public abstract class NotificationBaseAO<Entity extends NotificationBaseEntity> implements PanacheRepository<Entity> {

    public Entity markAsRead(Long notificationId, String userId) {
        Entity notification = find(
                "id = ?1 and recipientUserId = ?2",
                notificationId,
                userId
        ).firstResult();
        if (notification == null) {
            return null;
        }
        notification.setStatus(NotificationStatus.READ);
        notification.setReadAt(new Date());
        return notification;
    }

    public Entity archive(Long notificationId, String userId) {
        Entity notification = find(
                "id = ?1 and recipientUserId = ?2",
                notificationId,
                userId
        ).firstResult();
        if (notification == null) {
            return null;
        }
        notification.setStatus(NotificationStatus.ARCHIVED);
        notification.setArchivedAt(new Date());
        return notification;
    }

    public Entity markAsDelivered(Long notificationId, String userId) {
        Entity notification = find(
                "id = ?1 and recipientUserId = ?2",
                notificationId,
                userId
        ).firstResult();
        if (notification == null) {
            return null;
        }
        notification.setStatus(NotificationStatus.DELIVERED);
        notification.setDeliveredAt(new Date());
        notification.setDeliveryAttemptCount(notification.getDeliveryAttemptCount() + 1);
        return notification;
    }

    public Entity markAsDeliveryFailed(Long notificationId, String userId, String errorMessage) {
        Entity notification = find(
                "id = ?1 and recipientUserId = ?2",
                notificationId,
                userId
        ).firstResult();
        if (notification == null) {
            return null;
        }
        notification.setStatus(NotificationStatus.FAILED);
        notification.setDeliveredFailedAt(new Date());
        notification.setDeliveryAttemptCount(notification.getDeliveryAttemptCount() + 1);
        notification.setDeliveryErrorMessage(errorMessage);
        return notification;
    }

    @Transactional
    public Entity createTransactional(Entity e) {
        persistAndFlush(e);
        return e;
    }
}
