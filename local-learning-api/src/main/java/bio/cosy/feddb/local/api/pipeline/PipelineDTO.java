package bio.cosy.feddb.local.api.pipeline;

import bio.cosy.feddb.core.api.pipeline.BasePipelineDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class PipelineDTO extends BasePipelineDTO {
    private Long codeTransformerId;
}
