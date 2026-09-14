package bio.cosy.feddb.core.api.model;

import bio.cosy.feddb.core.api.app.AppPublishInfoDTO;
import bio.cosy.feddb.core.api.pipeline.PipelineStatus;
import bio.cosy.feddb.core.base.BaseDTO;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;
import java.util.Optional;


@EqualsAndHashCode(callSuper = true)
@Data
public class ModelSubDTO extends BaseDTO {

    private String imageName;

    private AppPublishInfoDTO publishInfo;

    private String publishHash;

    private String modelName;

    private String modelPath;

    @NotNull(message = "Model sub status is mandatory")
    private ModelSubStatus status;

    @NotNull(message = "Model version id is mandatory")
    private Long modelVersionId;
    private Long modelId;

    private Long experimentRunId;
    private Long federatedExperimentId;

    private PipelineStatus pipelineStatus;
    private Long pipelineId;

    private List<ModelSubFileDTO> files;

    private Optional<FederatedModelSubDetailDataDTO> federatedData = Optional.empty();
}
