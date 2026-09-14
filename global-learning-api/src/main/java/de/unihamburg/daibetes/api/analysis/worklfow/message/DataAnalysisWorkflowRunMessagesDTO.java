package de.unihamburg.daibetes.api.analysis.worklfow.message;

import bio.cosy.feddb.core.api.model.prediction.DataAnalysisRunModesEnum;
import bio.cosy.feddb.core.api.run.message.RunMessageDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class DataAnalysisWorkflowRunMessagesDTO extends RunMessageDTO {
    private DataAnalysisRunModesEnum runMode;

    private Long predictionId;
}
