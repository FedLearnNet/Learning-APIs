package bio.cosy.feddb.core.services.orch.dto;

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

    private List<String> environments = new ArrayList<>();

    public void addEnvironment(String token, Long id, String secret) {
        environments.add("DOCKER_LOGIN=True");
        environments.add("OAUTH_TOKEN=" + token);
        environments.add("PIPELINE_ID=" + id);
        environments.add("PIPELINE_SECRET=" + secret);
    }
}
