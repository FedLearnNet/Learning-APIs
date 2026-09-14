package bio.cosy.feddb.core.agent.judge;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LLMJudgeVerdictDTO {

    private boolean correct;
    private int score;
    private String explanation;
    private boolean complete;
    private boolean comprehensible;
    private boolean groundedInExpectation;

    public LLMJudgeVerdictDTO(String explanation) {
        this.correct = false;
        this.score = 0;
        this.explanation = explanation;
        this.complete = false;
        this.comprehensible = false;
        this.groundedInExpectation = false;
    }
}
