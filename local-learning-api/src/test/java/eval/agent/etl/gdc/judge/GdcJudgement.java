package eval.agent.etl.gdc.judge;

import dev.langchain4j.model.output.structured.Description;
import lombok.Data;

@Data
public class GdcJudgement {

    @Description("Whether the columns record the information of the GDC field")
    private boolean correct;

    @Description("Why, in one plain sentence")
    private String reason;
}
