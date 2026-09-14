package bio.cosy.feddb.core.api.notification;

import bio.cosy.feddb.core.base.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;

@Setter
@Getter
@MappedSuperclass
public class NotificationBaseEntity extends BaseEntity {

    @Column(nullable = false)
    public String recipientUserId;

    @Column(nullable = false)
    public String title;

    @Column(nullable = false, length = 4000)
    public String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public NotificationType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public NotificationPriority priority;

    public String sourceService;
    public String sourceEntityType;
    public String sourceEntityId;

    public String actionUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public NotificationStatus status = NotificationStatus.PENDING;

    public Integer deliveryAttemptCount = 0;
    public String deliveryErrorMessage;
    public Date deliveredAt;
    public Date deliveredFailedAt;

    public Date readAt;
    public Date archivedAt;
}
