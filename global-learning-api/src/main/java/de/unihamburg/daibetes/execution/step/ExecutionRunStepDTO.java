package de.unihamburg.daibetes.execution.step;

import bio.cosy.feddb.core.execution.step.BaseExecutionRunStepDTO;
import de.unihamburg.daibetes.execution.federation.ExecutionFederationDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ExecutionRunStepDTO extends BaseExecutionRunStepDTO {
    // What a step without a workflow node runs.
    private Long federatedAppVersionId;
    private Long modelSubId;
    private Long modelId;
    private ExecutionFederationDTO federation;
}
