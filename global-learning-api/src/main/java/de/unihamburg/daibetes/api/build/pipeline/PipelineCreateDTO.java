package de.unihamburg.daibetes.api.build.pipeline;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class PipelineCreateDTO {
    @NotNull
    private PipelineType pipelineType;

    private Boolean autoStart = false;
    private Long modelSubId;
    private Long appVersionId;
}
