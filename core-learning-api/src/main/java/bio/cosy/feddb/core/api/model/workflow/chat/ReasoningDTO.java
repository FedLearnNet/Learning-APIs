package bio.cosy.feddb.core.api.model.workflow.chat;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReasoningDTO {
    PlanStep planStep;
    private String message;
    private Date timestamp;

    public ReasoningDTO(String message) {
        this.message = message;
        this.timestamp = new Date();
    }

    public ReasoningDTO(PlanStep step) {
        this.message = step.getReason();
        this.planStep = step;
        this.timestamp = new Date();
    }
}
