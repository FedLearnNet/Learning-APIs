package bio.cosy.feddb.core.api.run.message;

import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class RunMessageLogDTO extends RunMessageDTO {

    private String severity;
    private String message;
    private String caller;
    private String stackTrace;
    private String group;

    //for federated runs, the workerId is the participantId
    //for other types, this isnt used yet, but could be
    private String workerId;

}
