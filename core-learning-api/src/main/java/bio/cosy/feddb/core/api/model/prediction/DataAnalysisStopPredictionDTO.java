package bio.cosy.feddb.core.api.model.prediction;

import lombok.Data;


/**
 * Data Transfer Object for stopping model predictions.
 */
@Data
public class DataAnalysisStopPredictionDTO {

    //if executing tool is a workflow
    private Long workflowId;
}

