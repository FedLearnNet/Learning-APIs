package bio.cosy.feddb.local.agent.etl.transform.bot;

import dev.langchain4j.model.output.structured.Description;
import lombok.Data;

@Data
public class LossReview {

    @Description("True when every loss follows the rules, false when a value loses a meaning it should keep")
    private boolean keep;

    @Description("For false: the values and what they should become instead; empty for true")
    private String reason;
}
