package de.unihamburg.daibetes.api.build.pipeline;

import bio.cosy.feddb.core.api.pipeline.BasePipelineDTO;
import bio.cosy.feddb.core.api.pipeline.PipelineRunMode;
import de.unihamburg.daibetes.api.app.version.FederatedAppPublishDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class PipelineDTO extends BasePipelineDTO {
    private PipelineType pipelineType;

    private PipelineRunMode runMode = PipelineRunMode.BUILD_ONLY;

    private FederatedAppPublishDTO publishRequest;

    private String secret;
    private Long modelSubId;
    private Long appVersionId;
}
