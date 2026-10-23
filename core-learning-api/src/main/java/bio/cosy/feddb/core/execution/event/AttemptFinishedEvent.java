package bio.cosy.feddb.core.execution.event;

import bio.cosy.feddb.core.execution.run.types.RunStatusTypes;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AttemptFinishedEvent {
    private Long attemptId;
    private RunStatusTypes status;
}
