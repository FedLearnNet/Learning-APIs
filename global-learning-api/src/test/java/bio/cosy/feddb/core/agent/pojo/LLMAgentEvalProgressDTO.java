package bio.cosy.feddb.core.agent.pojo;

import lombok.Data;

@Data
public class LLMAgentEvalProgressDTO {

    private int globalProcessed;
    private int globalExecuted;
    private int globalSkipped;
    private int globalWarnings;
    private int globalFailed;

    public void increaseProcessed() {
        this.globalProcessed++;
    }

    public void increaseExecuted() {
        this.globalExecuted++;
    }

    public void increaseSkipped() {
        this.globalSkipped++;
    }

    public void increaseWarnings() {
        this.globalWarnings++;
    }

    public void increaseFailed() {
        this.globalFailed++;
    }
}
