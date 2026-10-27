package bio.cosy.feddb.local.execution.attempt;

import bio.cosy.feddb.core.execution.attempt.BaseDetailResultDTO;
import bio.cosy.feddb.local.execution.file.ExecutionFileDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ExecutionAttemptDTO extends BaseDetailResultDTO<ExecutionFileDTO> {
}
