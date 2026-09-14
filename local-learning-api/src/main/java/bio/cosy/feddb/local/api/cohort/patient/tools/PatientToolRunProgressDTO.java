package bio.cosy.feddb.local.api.cohort.patient.tools;

import bio.cosy.feddb.core.api.run.message.RunMessageLogDTO;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class PatientToolRunProgressDTO {
    private Long runId;
    private PatientToolRunStatusDTO status;
    private List<RunMessageLogDTO> logs = new ArrayList<>();
    private List<PatientToolRunOutputDTO> outputs = new ArrayList<>();
    private boolean downloadReady;
}
