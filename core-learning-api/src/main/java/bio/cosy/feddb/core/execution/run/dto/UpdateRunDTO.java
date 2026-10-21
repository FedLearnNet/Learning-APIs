package bio.cosy.feddb.core.execution.run.dto;

import bio.cosy.feddb.core.execution.run.types.RunStatusTypes;
import lombok.Data;

@Data
public class UpdateRunDTO {

    private Long runId;
    private RunStatusTypes status;
    private String error;
    private Float progress;

}
