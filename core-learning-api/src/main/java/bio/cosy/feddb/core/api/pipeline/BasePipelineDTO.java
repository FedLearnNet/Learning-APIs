package bio.cosy.feddb.core.api.pipeline;

import bio.cosy.feddb.core.api.app.AppPublishInfoDTO;
import bio.cosy.feddb.core.base.BaseDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
public class BasePipelineDTO extends BaseDTO {
    private PipelineStatus pipelineStatus;
    private String dockerTag;
    private String containerId;
    private AppPublishInfoDTO publishInfo;
    private List<PipelineStepDTO> pipelineSteps;
}
