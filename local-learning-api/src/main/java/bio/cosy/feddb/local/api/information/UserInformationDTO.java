package bio.cosy.feddb.local.api.information;

import bio.cosy.feddb.local.api.notification.NotificationDTO;
import lombok.Data;

import java.util.List;

@Data
public class UserInformationDTO {
    private List<NotificationDTO> notifications;
    private Long openTrainingRequests;
    private Long openStatisticsRequests;
    private Long openMetricsRequests;
}
