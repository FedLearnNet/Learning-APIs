package bio.cosy.feddb.core.execution.event;

import bio.cosy.feddb.core.execution.ExecutionPurpose;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AttemptLaunchEvent {
    private Long attemptId;
    private ExecutionPurpose purpose;
}
