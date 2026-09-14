package bio.cosy.feddb.core.api.model.prediction;

import bio.cosy.feddb.core.base.BaseAuthDTO;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.LinkedHashMap;


/**
 * Data Transfer Object for creating model predictions.
 * It contains the following fields:
 * - {@code hyperParams}: A mapping of hyperparameter keys and their associated values.
 * - {@code inputs}: A mapping of input keys and their respective values required for model execution.
 * - {@code modelSubId}: The identifier of the sub-model to be utilized during prediction. If null latest is taken
 * - {@code modelVersionId}: The identifier of the modelVersion to be utilized during prediction.
 * This class is commonly used with orchestrators like {@code ModelOrchestrator} to execute models
 * and manage workflows for predictive tasks.
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class DataAnalysisCreatePredictionDTO extends BaseAuthDTO {

    private LinkedHashMap<String, Object> hyperParams;
    @NotNull(message = "Inputs must not be null")
    private LinkedHashMap<String, Object> inputs;

    //if executing tool is a model
    private Long modelSubId;
    private Long modelVersionId;

    //if executing tool is an app
    private Long appVersionId;

    //if executing tool is a workflow
    private Long workflowId;
}

