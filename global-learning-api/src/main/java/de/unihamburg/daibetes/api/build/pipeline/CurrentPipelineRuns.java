package de.unihamburg.daibetes.api.build.pipeline;

import lombok.Data;

@Data
public class CurrentPipelineRuns {
    private Long pipelineId;
    private String token;
}
