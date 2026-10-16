package bio.cosy.feddb.core.api.pipeline;

import lombok.Data;

@Data
public class PipelineRunInfoDTO {
    private String gitRepoUrl;
    private String imageName;
    private String commitHash;
}
