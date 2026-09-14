package bio.cosy.feddb.core.api.run.message;

import bio.cosy.feddb.core.base.BaseDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class RunMessageDTO extends BaseDTO {

    private String process;
    private RunMessageTypes type;
    private String message;

    private Long runId;

    //for federated runs, the workerId is the participantId
    private String workerId;
}
