package bio.cosy.feddb.core.agent.pojo;

import bio.cosy.feddb.core.agent.judge.LLMJudgeVerdictDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.List;

@Data
public class LLMAgentQuestionResult {
    private String topic;
    private int depth;
    private LLMAgentAnswer answer;
    private LLMJudgeVerdictDTO judgeVerdict;

    private String question;
    private List<String> neededTools;
    private String expectedAnswer;
    private String hitlAnswer;
    private List<LLMAgentFileFixture> requiredFiles = new ArrayList<>();
}
