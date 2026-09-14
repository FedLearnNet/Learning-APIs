package bio.cosy.feddb.core.api.run;

import lombok.Data;

@Data
public class UpdateRunDTO {

    private Long runId;
    private RunStatusTypes status;
    private String error;
    private Float progress;

}
