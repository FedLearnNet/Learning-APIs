package de.unihamburg.daibetes.api.analysis.run;

import bio.cosy.feddb.core.api.model.workflow.DataAnalysisFileDTO;
import bio.cosy.feddb.core.execution.attempt.BaseDetailResultDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class DataAnalysisAttemptDTO extends BaseDetailResultDTO<DataAnalysisFileDTO> {
}
