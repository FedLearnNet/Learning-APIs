package bio.cosy.feddb.local.agent.etl.transform.bot;

import dev.langchain4j.model.output.structured.Description;
import lombok.Data;

@Data
public class ColumnReview {

    @Description("True when the selected columns record the information of the schema node")
    private boolean held;

    @Description("For false: why not, and the column that records the information if there is one; empty for true")
    private String reason;
}
