package bio.cosy.feddb.local.api.learning.request.metrics;

import bio.cosy.feddb.core.api.socket.RunResultRequestType;
import bio.cosy.feddb.core.base.BaseDTO;
import bio.cosy.feddb.local.api.learning.request.LearningRequestStatus;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;
import java.util.List;
import java.util.UUID;

@EqualsAndHashCode(callSuper = true)
@Data
public class RequestRunMetricsDTO extends BaseDTO {

    private String requestKeycloakId;
    private UUID globalRequestId;
    private RunResultRequestType type;
    private Date verifiedOn;
    private String verifiedByKeycloakId;
    private LearningRequestStatus status = LearningRequestStatus.PENDING;
    private Long projectId;
    private String projectName;
    private String experimentGlobalUniqueId;
    private List<String> metricNames;
}
