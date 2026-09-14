package bio.cosy.feddb.local.api.cohort.patient.traceability.learning;

import bio.cosy.feddb.core.base.BaseDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@Data
public class PatientLearningDTO extends BaseDTO {

    private String projectName;
    private String cohortName;

    private String externalPatientId;

    private Long internalCohortId;
    private Long internalPatientId;
    private Long patientId;
    private Long requestId;
}
