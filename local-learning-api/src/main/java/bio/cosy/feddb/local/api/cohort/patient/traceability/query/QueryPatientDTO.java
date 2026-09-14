package bio.cosy.feddb.local.api.cohort.patient.traceability.query;

import bio.cosy.feddb.core.base.BaseDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class QueryPatientDTO extends BaseDTO {

    private String queryId;
    private Long patientId;
    private Long cohortId;
}
