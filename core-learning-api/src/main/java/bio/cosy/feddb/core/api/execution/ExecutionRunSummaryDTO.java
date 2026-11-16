package bio.cosy.feddb.core.api.execution;

import bio.cosy.feddb.core.execution.BaseExecutionStatusDTO;
import bio.cosy.feddb.core.execution.ExecutionLocation;
import bio.cosy.feddb.core.execution.ExecutionMode;
import bio.cosy.feddb.core.execution.ExecutionPurpose;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.UUID;

@Data
@EqualsAndHashCode(callSuper = true)
public class ExecutionRunSummaryDTO extends BaseExecutionStatusDTO {
    private UUID publicId;
    private String name;
    private ExecutionPurpose purpose;
    private ExecutionMode mode;
    private ExecutionLocation location;
    private String keycloakId;
    private int stepCount;
    private int attemptCount;
}
