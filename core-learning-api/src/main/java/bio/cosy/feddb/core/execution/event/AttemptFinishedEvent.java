package bio.cosy.feddb.core.execution.event;

import bio.cosy.feddb.core.execution.ExecutionPurpose;
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
    // Carried along, so the container is stopped and the key revoked even when the run is deleted meanwhile.
    private String containerId;
    private ExecutionPurpose purpose;
}
