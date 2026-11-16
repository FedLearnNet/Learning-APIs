package de.unihamburg.daibetes.api.build.pipeline;

import bio.cosy.feddb.core.api.pipeline.PipelineRunMode;
import de.unihamburg.daibetes.api.app.version.FederatedAppPublishDTO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class PipelineCreateDTO {
    @NotNull
    private PipelineType pipelineType;

    @NotNull
    private PipelineRunMode runMode = PipelineRunMode.BUILD_ONLY;

    // required for ALL, applied once the build succeeded
    @Valid
    private FederatedAppPublishDTO publishRequest;

    private Boolean autoStart = false;
    private Long modelSubId;
    private Long appVersionId;
}
