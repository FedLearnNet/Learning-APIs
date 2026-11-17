package bio.cosy.feddb.core.api.pipeline;

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
