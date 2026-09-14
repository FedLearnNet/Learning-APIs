package bio.cosy.feddb.core.api.model.workflow.chat;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class HumanInTheLoopDTO {
    private String question;
    private String answer;

    private Date timestamp;

    public HumanInTheLoopDTO(String message) {
        this.question = message;
        this.timestamp = new Date();
    }

    public HumanInTheLoopDTO(String question, String answer) {
        this.question = question;
        this.answer = answer;
        this.timestamp = new Date();
    }
}
