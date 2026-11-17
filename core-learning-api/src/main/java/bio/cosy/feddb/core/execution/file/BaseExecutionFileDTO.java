package bio.cosy.feddb.core.execution.file;

import bio.cosy.feddb.core.api.file.FileDTO;
import bio.cosy.feddb.core.base.BaseDTO;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.File;

@Data
@EqualsAndHashCode(callSuper = true)
public class BaseExecutionFileDTO extends BaseDTO {
    private Long attemptId;

    // Input or output name the app knows the file by.
    private String name;
    private ExecutionFileDirection direction;
    private FileDTO file;

    // Temporary copy of the stored file, only used for internal logics
    @JsonIgnore
    private File content;

    @JsonIgnore
    public String getFileNameWithExtension() {
        if (file == null) {
            return name;
        }
        String extension = FilenameUtils.getExtension(file.getFileName());
        if (StringUtils.isEmpty(extension)) {
            return name;
        }
        return name + "." + extension;
    }
}
