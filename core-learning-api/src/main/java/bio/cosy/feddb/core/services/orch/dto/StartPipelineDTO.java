package bio.cosy.feddb.core.services.orch.dto;

import bio.cosy.feddb.core.api.pipeline.PipelineRunMode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;


@Data
public class StartPipelineDTO {

    @NotNull(message = "pipelineId cannot be null")
    private Long pipelineId;

    @NotBlank(message = "appImage cannot be blank")
    private String appImage;

    @NotNull
    private PipelineRunMode runMode = PipelineRunMode.ALL;

    private List<String> environments = new ArrayList<>();

    private boolean localOnly;

    public void addEnvironment(String apiKey, Long id, String secret) {
        environments.add("PIPELINE_API_KEY=" + apiKey);
        addEnvironment(id, secret);
    }

    public void addEnvironment(Long id, String secret) {
        environments.add("PIPELINE_ID=" + id);
        environments.add("PIPELINE_SECRET=" + secret);
    }
}
