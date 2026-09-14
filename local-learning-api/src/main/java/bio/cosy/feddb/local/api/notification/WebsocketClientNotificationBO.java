package bio.cosy.feddb.local.api.notification;

import bio.cosy.feddb.core.api.notification.CreateNotificationDTO;
import bio.cosy.feddb.core.api.notification.NotificationPriority;
import bio.cosy.feddb.core.api.notification.NotificationType;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Set;

@ApplicationScoped
public class WebsocketClientNotificationBO {

    private static final String SOURCE_SERVICE = "websocket-client";

    @Inject
    NotificationBO notificationBO;

    public void notifyTrainingRequestReceived(Set<Long> cohortIds) {
        notifyCohorts(
                cohortIds,
                "Training request received",
                "A new training request is waiting for review.",
                "federated-learning-request"
        );
    }

    public void notifyStatisticsRequestReceived(Set<Long> cohortIds) {
        notifyCohorts(
                cohortIds,
                "Statistics request received",
                "A new statistics request is waiting for review.",
                "data-statistics-request"
        );
    }

    public void notifyRunMetricsRequestReceived(Set<Long> cohortIds) {
        notifyCohorts(
                cohortIds,
                "Run metrics request received",
                "A new run metrics request is waiting for review.",
                "run-metrics-request"
        );
    }

    public void notifyConnectorImportCompleted(Long cohortId) {
        notifyCohorts(
                Set.of(cohortId),
                "Connector import completed",
                "A connector import has completed and is ready for review.",
                "connector-import"
        );
    }

    private void notifyCohorts(Set<Long> cohortIds, String title, String message, String sourceEntityType) {
        if (cohortIds == null || cohortIds.isEmpty()) {
            return;
        }
        CreateNotificationDTO dto = buildNotification(title, message, sourceEntityType);
        notificationBO.fireAndSendForCohorts(dto, cohortIds);
    }

    private CreateNotificationDTO buildNotification(String title, String message, String sourceEntityType) {
        CreateNotificationDTO dto = new CreateNotificationDTO();
        dto.setTitle(title);
        dto.setMessage(message);
        dto.setType(NotificationType.ACTION_REQUIRED);
        dto.setPriority(NotificationPriority.NORMAL);
        dto.setSourceService(SOURCE_SERVICE);
        dto.setSourceEntityType(sourceEntityType);
        return dto;
    }
}
