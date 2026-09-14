package bio.cosy.feddb.core.api.model.workflow;

import bio.cosy.feddb.core.api.model.workflow.chat.DataAnalysisChatWrapperDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;


/**
 * Data Transfer Object for representing the inputs and related metadata
 * for a model experiment process.
 * Its a group of {@code ModelPredictionDTO} objects.
 * Contains the ModelPredictionDTO objects.
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class DataAnalysisDetailDTO extends ModelWorkflowDTO {

    private List<DataAnalysisChatWrapperDTO> messages;
    private List<DataAnalysisFileDTO> files;
}

