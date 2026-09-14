package bio.cosy.feddb.local.api.notification;

import bio.cosy.feddb.core.api.notification.NotificationBaseAO;
import bio.cosy.feddb.core.api.notification.NotificationStatus;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class NotificationAO extends NotificationBaseAO<NotificationEntity> {
    public List<NotificationEntity> findActiveForUser(String keycloakId) {
        return find(
                "recipientUserId = ?1 and status <> ?2 order by createdAt desc",
                keycloakId,
                NotificationStatus.ARCHIVED
        ).list();
    }

    public Optional<NotificationEntity> findByIdAndUser(Long id, String keycloakId) {
        return find("id = ?1 and recipientUserId = ?2", id, keycloakId).firstResultOptional();
    }

    public boolean deleteByIdAndUser(Long id, String keycloakId) {
        return delete("id = ?1 and recipientUserId = ?2", id, keycloakId) == 1;
    }
}
