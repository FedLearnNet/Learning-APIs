package de.unihamburg.daibetes.api.build.pipeline;

import lombok.Data;

@Data
public class PipelineRunInfoDTO {
    private String gitRepoUrl;
    private String imageName;
    private String dockerTag;
}
