package bio.cosy.feddb.local.api.statistics.request;

import bio.cosy.feddb.core.base.BaseDTO;
import bio.cosy.feddb.local.api.learning.request.LearningRequestStatus;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;
import java.util.List;
import java.util.UUID;

@EqualsAndHashCode(callSuper = true)
@Data
public class RequestDataStatisticsDTO extends BaseDTO {

    private String requestKeycloakId;

    private Date verifiedOn;

    private String verifiedByKeycloakId;

    private LearningRequestStatus status = LearningRequestStatus.PENDING;

    private UUID requestId;

    private Long queryId;
    private List<Long> cohortIds;
    private List<Long> patientIds;
}
