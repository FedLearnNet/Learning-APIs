package bio.cosy.feddb.local.api.learning.request.metrics;

import bio.cosy.feddb.core.base.BaseDTO;
import bio.cosy.feddb.local.api.learning.request.FederatedLearningRequestStatus;
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
    private Date verifiedOn;
    private String verifiedByKeycloakId;
    private FederatedLearningRequestStatus status = FederatedLearningRequestStatus.PENDING;
    private Long projectId;
    private String projectName;
    private String experimentGlobalUniqueId;
    private List<String> metricNames;
}
