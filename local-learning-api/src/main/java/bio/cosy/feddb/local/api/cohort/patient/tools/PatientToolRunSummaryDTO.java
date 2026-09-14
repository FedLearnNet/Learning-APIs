package bio.cosy.feddb.local.api.cohort.patient.tools;

import bio.cosy.feddb.core.api.run.RunStatusTypes;
import lombok.Data;

import java.util.Date;
import java.util.LinkedHashMap;

@Data
public class PatientToolRunSummaryDTO {
    private Long id;
    private String appName;
    private String image;
    private Long globalAPPVersionId;
    private RunStatusTypes runStatus;
    private String lastError;
    private Date startedAt;
    private Date finishedAt;
    private LinkedHashMap<String, Object> hyperParams;
    private String outputFileName;
    private Long outputSize;
    private boolean downloadReady;
}
