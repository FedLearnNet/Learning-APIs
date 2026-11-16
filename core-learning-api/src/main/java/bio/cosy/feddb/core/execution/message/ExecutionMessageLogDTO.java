package bio.cosy.feddb.core.execution.message;

import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class ExecutionMessageLogDTO extends ExecutionMessageDTO {

    private String severity;
    private String message;
    private String caller;
    private String stackTrace;
    private String group;
}
