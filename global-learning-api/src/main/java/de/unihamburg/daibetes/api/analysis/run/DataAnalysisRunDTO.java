package de.unihamburg.daibetes.api.analysis.run;

import de.unihamburg.daibetes.execution.run.ExecutionRunDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class DataAnalysisRunDTO extends ExecutionRunDTO {
    // Empty for a run started outside a data analysis.
    private Long dataAnalysisId;
    // The workflow the run executes, empty for a single app or model.
    private Long workflowId;
}
