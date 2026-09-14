package bio.cosy.feddb.local.api.cohort.patient.tools;

import bio.cosy.feddb.core.api.run.RunStatusTypes;
import lombok.Data;

import java.util.Date;

@Data
public class PatientToolRunStatusDTO {
    private Long runId;
    private RunStatusTypes runStatus;
    private Float progress;
    private String lastError;
    private Date startedAt;
    private Date finishedAt;
}
