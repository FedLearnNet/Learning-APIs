package bio.cosy.feddb.local.api.cohort.patient.tools;

import bio.cosy.feddb.core.api.run.RunStatusTypes;
import bio.cosy.feddb.core.base.BaseDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;
import java.util.LinkedHashMap;

@EqualsAndHashCode(callSuper = true)
@Data
public class PatientToolRunDTO extends BaseDTO {

    private RunStatusTypes runStatus;

    private String lastError;

    private Float progress;

    private String containerId;

    private Date finishedAt;

    private Date startedAt;

    private PatientToolRunType toolType;

    private LinkedHashMap<String, Object> hyperParams;

    private String image;
    private String error;
    private Long globalAPPVersionId;

    private Long cohortId;

    private String appName;


}
