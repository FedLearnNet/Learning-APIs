package bio.cosy.feddb.core.api.model.workflow;

import bio.cosy.feddb.core.base.BaseAuthDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;


/**
 * Data Transfer Object for representing the inputs and related metadata
 * for a model experiment process.
 * Its a group of {@code ModelPredictionDTO} objects.
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class ModelWorkflowDTO extends BaseAuthDTO {
    private String llmSummary;
    private String name;
}

