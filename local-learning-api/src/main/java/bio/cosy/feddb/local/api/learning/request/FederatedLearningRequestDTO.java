package bio.cosy.feddb.local.api.learning.request;

import bio.cosy.feddb.core.api.project.ProjectDetailDTO;
import bio.cosy.feddb.core.base.BaseDTO;
import bio.cosy.feddb.local.api.cohort.patient.traceability.learning.PatientLearningDTO;
import bio.cosy.feddb.local.api.learning.request.cohort.FederatedLearningRequestCohortDTO;
import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private Map<Long, Long> patientCountByCohort;

    private List<FederatedLearningRequestCohortDTO> cohortDecisions;

    private boolean awaitingCurrentUserDecision;

    public Map<Long, Long> getPatientCountByCohort() {
        return patientCountByCohort == null ? null : Map.copyOf(patientCountByCohort);
    }

    public void setPatientCountByCohort(final Map<Long, Long> patientCountByCohort) {
        this.patientCountByCohort = patientCountByCohort == null
                ? null
                : new HashMap<>(patientCountByCohort);
    }
}
