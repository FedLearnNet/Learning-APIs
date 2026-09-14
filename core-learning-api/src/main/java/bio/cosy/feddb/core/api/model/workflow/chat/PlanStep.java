package bio.cosy.feddb.core.api.model.workflow.chat;

import lombok.Data;

@Data
public class PlanStep {
    private PlanAction action;
    private String reason;
    private String subTaskQuestion;
}
