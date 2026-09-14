package bio.cosy.feddb.core.api.model.prediction;

import bio.cosy.feddb.core.api.model.workflow.DataAnalysisFileDTO;
import bio.cosy.feddb.core.api.run.RunMetaDTO;
import bio.cosy.feddb.core.api.run.RunStatusTypes;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.LinkedHashMap;
import java.util.List;


/**
 * Data Transfer Object for representing the status, output, and related metadata
 * for a model prediction process.
 * <p>
 * This class extends {@code ModelCreatePredictionDTO}, inheriting the definition
 * of hyperparameters, input data, and sub-model identifiers used to initiate
 * predictive tasks. In addition, it includes fields to track the runtime status,
 * logging, errors, results, and containerization details for the prediction process.
 * <p>
 * Fields:
 * - {@code status}: Defines the current status of the prediction using {@code RunStatusTypes}.
 * - {@code lastLog}: Stores the most recent log message generated during prediction.
 * - {@code lastError}: Captures the most recent error message encountered.
 * - {@code result}: Conveys the outcome of the prediction as a map of key-value pairs.
 * - {@code containerId}: Represents the identifier of the container used for executing the prediction.
 * - {@code modelId}: Refers to the specific model used for the prediction.
 * - {@code modelName}: Denotes the name of the model used in the prediction.
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class DataAnalysisPredictionDTO extends DataAnalysisCreatePredictionDTO {

    private RunStatusTypes status;

    private String lastLog;
    private String lastError;
    private String rawLog;

    private LinkedHashMap<String, Object> result;

    private List<DataAnalysisFileDTO> inputFiles;
    private List<DataAnalysisFileDTO> outputFiles;

    private String containerId;

    private Long modelId;
    private String name;
    private Long dataAnalysisId;

    // Run metadata (timings today). RUNTIME in meta.timings always present when measured; OVERHEAD_*
    // only when posymed.runtime.overhead.enabled is true (stripped before exposure otherwise).
    private RunMetaDTO meta;

    @JsonIgnore
    private String imageName;

    @JsonIgnore
    public boolean isFinished() {
        return status == RunStatusTypes.FINISHED || status == RunStatusTypes.ERROR || status == RunStatusTypes.STOPPED;
    }
}

