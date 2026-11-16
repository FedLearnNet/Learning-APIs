package bio.cosy.feddb.core.execution.run.dto;

import bio.cosy.feddb.core.execution.run.types.RunStatusTypes;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class UpdateRunDTO {

    private Long runId;
    private RunStatusTypes status;
    private String error;
    private Float progress;

    public UpdateRunDTO(Long runId, RunStatusTypes status, String error) {
        this.runId = runId;
        this.status = status;
        this.error = error;
    }
}
