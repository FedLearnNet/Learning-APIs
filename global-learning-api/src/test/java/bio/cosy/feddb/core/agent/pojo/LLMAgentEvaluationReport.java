package bio.cosy.feddb.core.agent.pojo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class LLMAgentEvaluationReport {
    private String modelName;
    private String testSetPath;
    private List<LLMAgentQuestionResult> results = new ArrayList<>();
}
