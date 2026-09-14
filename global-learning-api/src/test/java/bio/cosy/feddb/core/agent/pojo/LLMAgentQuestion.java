package bio.cosy.feddb.core.agent.pojo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class LLMAgentQuestion {
    private String question;
    private List<String> neededTools;
    private String expectedAnswer;
    private String hitlAnswer;

    private List<LLMAgentFileFixture> requiredFiles = new ArrayList<>();
}
