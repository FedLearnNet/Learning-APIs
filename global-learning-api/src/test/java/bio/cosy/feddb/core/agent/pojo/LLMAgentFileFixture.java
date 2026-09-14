package bio.cosy.feddb.core.agent.pojo;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class LLMAgentFileFixture {


    private String resourcePath;


    private String displayName;

    private String kind;

    private String source;
}
