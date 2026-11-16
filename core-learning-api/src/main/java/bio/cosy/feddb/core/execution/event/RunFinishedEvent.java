package bio.cosy.feddb.core.execution.event;

import bio.cosy.feddb.core.execution.run.types.RunStatusTypes;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RunFinishedEvent {
    private Long runId;
    private RunStatusTypes status;
}
