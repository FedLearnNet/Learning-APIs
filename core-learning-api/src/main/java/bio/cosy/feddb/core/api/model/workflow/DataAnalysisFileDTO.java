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

    private String outputName;
    private String inputName;
    // Optional association to the execution attempt that read or wrote the file
    private Long attemptId;


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
