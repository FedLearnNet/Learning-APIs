package bio.cosy.feddb.core.api.notification;

import lombok.Data;

@Data
public class CreateNotificationDTO {
    public String title;
    public String message;
    public NotificationType type;
    public NotificationPriority priority;
    public String sourceService;
    public String sourceEntityType;
    public String sourceEntityId;
    public String actionUrl;
}
