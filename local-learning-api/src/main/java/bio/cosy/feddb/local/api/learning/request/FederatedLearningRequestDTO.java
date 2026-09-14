package bio.cosy.feddb.local.api.learning.request;

import bio.cosy.feddb.core.api.project.ProjectDetailDTO;
import bio.cosy.feddb.core.base.BaseDTO;
import bio.cosy.feddb.local.api.cohort.patient.traceability.learning.PatientLearningDTO;
import bio.cosy.feddb.local.api.learning.request.cohort.FederatedLearningRequestCohortDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
public class FederatedLearningRequestDTO extends BaseDTO {
    private String globalFLExperimentUniqueId;
    private FederatedLearningRequestStatus status;

    private Boolean modelNeedToBePublic;
    private Boolean modelCanBePublic;

    private String channelId;

    private ProjectDetailDTO project;
    private List<PatientLearningDTO> requestPatients;

    private List<FederatedLearningRequestCohortDTO> cohortDecisions;

    private boolean awaitingCurrentUserDecision;
}
