package bio.cosy.feddb.core.execution.event;

import lombok.Data;
import lombok.experimental.Accessors;

/** Something of a run changed: the run itself, one of its steps or one of its attempts. */
@Data
@Accessors(chain = true)
public class ExecutionRunChange {
    private Long runId;
    private Long stepId;
    private Long attemptId;
}
