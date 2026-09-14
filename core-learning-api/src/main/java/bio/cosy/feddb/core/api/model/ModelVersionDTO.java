package bio.cosy.feddb.core.api.model;

import bio.cosy.feddb.core.base.BaseDTO;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;


@EqualsAndHashCode(callSuper = true)
@Data
public class ModelVersionDTO extends BaseDTO {

    @Pattern(
            regexp = "^\\d+\\.\\d+\\.\\d+$",
            message = "Version must be in the format 'major.minor.patch', e.g., '1.0.0'"
    )
    private String modelVersion;

    private Long modelId;
    private Long experimentId;
    private Long federatedExperimentId;

    private String changelog;

    private ModelPublishStatus publishStatus = ModelPublishStatus.PRIVATE;

    private ModelSubDTO selectedSubModel;
    private List<ModelSubDTO> subModels;
}

