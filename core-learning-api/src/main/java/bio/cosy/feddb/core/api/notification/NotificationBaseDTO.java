package bio.cosy.feddb.core.api.notification;

import bio.cosy.feddb.core.base.BaseDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

@EqualsAndHashCode(callSuper = true)
@Data
public class NotificationBaseDTO extends BaseDTO {
    public String recipientUserId;

    public String title;

    public String message;

    public NotificationType type;

    public NotificationPriority priority;

    public String sourceService;
    public String sourceEntityType;
    public String sourceEntityId;

    public String actionUrl;

    public NotificationStatus status = NotificationStatus.PENDING;

    public Integer deliveryAttemptCount = 0;
    public String deliveryErrorMessage;
    public Date deliveredAt;
    public Date deliveredFailedAt;

    public Date readAt;
    public Date archivedAt;
}
