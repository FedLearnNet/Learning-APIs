package bio.cosy.feddb.core.execution.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RunStartedEvent {
    private Long runId;
}
