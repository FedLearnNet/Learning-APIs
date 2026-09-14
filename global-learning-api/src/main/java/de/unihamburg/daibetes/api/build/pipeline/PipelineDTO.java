package de.unihamburg.daibetes.api.build.pipeline;

import bio.cosy.feddb.core.api.app.AppPublishInfoDTO;
import bio.cosy.feddb.core.api.pipeline.PipelineStatus;
import bio.cosy.feddb.core.base.BaseDTO;
import de.unihamburg.daibetes.api.build.pipeline.steps.PipelineStepDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
public class PipelineDTO extends BaseDTO {

    private PipelineStatus pipelineStatus;

    private PipelineType pipelineType;

    private List<PipelineStepDTO> pipelineSteps;

    private AppPublishInfoDTO publishInfo;

    private String secret;

    private String dockerTag;

    private Long modelSubId;
    private Long appVersionId;
    private String containerId;
}
