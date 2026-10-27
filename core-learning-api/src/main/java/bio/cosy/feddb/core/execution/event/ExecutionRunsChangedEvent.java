package bio.cosy.feddb.core.execution.event;

import bio.cosy.feddb.core.execution.ExecutionPurpose;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;


@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExecutionRunsChangedEvent {
    private ExecutionPurpose purpose;
    private List<Long> runIds;
}
