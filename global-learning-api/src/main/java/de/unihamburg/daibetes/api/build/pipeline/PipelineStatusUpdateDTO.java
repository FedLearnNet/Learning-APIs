package de.unihamburg.daibetes.api.build.pipeline;

import bio.cosy.feddb.core.api.pipeline.PipelineStatus;
import de.unihamburg.daibetes.api.build.pipeline.steps.StepName;
import lombok.Data;

@Data
public class PipelineStatusUpdateDTO {
    private StepName stepName;
    private PipelineStatus status;
    private String logs;
    private Integer progress;
    private String errorCode;
    private String errorMessage;
}
