package bio.cosy.feddb.local.execution.run;

import bio.cosy.feddb.core.execution.run.BaseExecutionRunDTO;
import bio.cosy.feddb.local.api.cohort.patient.tools.PatientToolRunType;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ExecutionRunDTO extends BaseExecutionRunDTO {
    private String keycloakId;
    private Long cohortId;
    private PatientToolRunType toolType;
    private Long connectorRunId;
    private String participantId;
}
