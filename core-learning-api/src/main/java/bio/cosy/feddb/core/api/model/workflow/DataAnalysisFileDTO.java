package bio.cosy.feddb.core.api.model.workflow;

import bio.cosy.feddb.core.api.file.FileDTO;
import bio.cosy.feddb.core.base.BaseAuthDTO;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Optional;

@EqualsAndHashCode(callSuper = true)
@Data
public class DataAnalysisFileDTO extends BaseAuthDTO {
    private Long dataAnalysisId;
    private FileDTO file;

    // Optional association to a prediction
    private String outputName;
    private String inputName;
    private Long predictionId;
    private Long workflowStepId;


    @JsonIgnore
    public boolean isInputFile() {
        return inputName != null && !inputName.isEmpty();
    }

    @JsonIgnore
    public String getRole() {
        return isInputFile() ? "input" : "output";
    }

    @JsonIgnore
    public String getName() {
        if (isInputFile()) {
            return inputName;
        } else {
            return Optional.ofNullable(outputName).orElseGet(() -> file.getFileName());
        }
    }
}
