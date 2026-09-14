package de.unihamburg.daibetes.api.build.pipeline.steps;

import bio.cosy.feddb.core.base.BaseDTO;
import bio.cosy.feddb.core.api.pipeline.PipelineStatus;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

@EqualsAndHashCode(callSuper = true)
@Data
public class PipelineStepDTO extends BaseDTO {

    private StepName name;
    private PipelineStatus stepStatus;
    private String logs;

    public Date startedAt;
    public Date finishedAt;
    public String errorCode;
    public String errorMessage;
    private Integer progress;

    private Long pipelineId;
}
