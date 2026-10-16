package de.unihamburg.daibetes.api.build.pipeline;

import bio.cosy.feddb.core.api.pipeline.BasePipelineDTO;
import bio.cosy.feddb.core.api.pipeline.PipelineRunMode;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class PipelineDTO extends BasePipelineDTO {
    private PipelineType pipelineType;

    private PipelineRunMode runMode = PipelineRunMode.ALL;

    private String secret;
    private Long modelSubId;
    private Long appVersionId;
}
