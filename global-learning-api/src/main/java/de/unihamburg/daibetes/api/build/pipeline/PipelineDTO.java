package de.unihamburg.daibetes.api.build.pipeline;

import bio.cosy.feddb.core.api.pipeline.BasePipelineDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class PipelineDTO extends BasePipelineDTO {
    private PipelineType pipelineType;
    private String secret;
    private Long modelSubId;
    private Long appVersionId;
}
