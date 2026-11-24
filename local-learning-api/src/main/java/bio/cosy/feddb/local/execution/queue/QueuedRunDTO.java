package bio.cosy.feddb.local.execution.queue;

import bio.cosy.feddb.core.execution.ExecutionMode;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class QueuedRunDTO {
    private Long runId;
    private String name;
    private ExecutionMode mode;
    private int position;
    private Date queuedAt;
}
