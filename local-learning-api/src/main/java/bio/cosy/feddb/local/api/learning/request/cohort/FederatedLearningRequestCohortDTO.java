package bio.cosy.feddb.local.api.learning.request.cohort;

import lombok.Data;

@Data
public class FederatedLearningRequestCohortDTO {
    private Long cohortId;
    private FederatedLearningRequestCohortStatus status;
    private boolean decidableByCurrentUser;
}
