package de.unihamburg.daibetes.api.build.pipeline;

import bio.cosy.feddb.core.api.pipeline.PipelineRunMode;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class PipelineCreateDTO {
    @NotNull
    private PipelineType pipelineType;

    @NotNull
    private PipelineRunMode runMode = PipelineRunMode.ALL;

    private Boolean autoStart = false;
    private Long modelSubId;
    private Long appVersionId;
}
