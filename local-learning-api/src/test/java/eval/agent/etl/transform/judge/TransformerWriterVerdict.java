package eval.agent.etl.transform.judge;

import com.fasterxml.jackson.annotation.JsonIgnore;
import dev.langchain4j.model.output.structured.Description;
import lombok.Data;

@Data
public class TransformerWriterVerdict {
    @Description("Whether the source columns hold the information of the schema node")
    private boolean mappingCorrect;

    @Description("Whether every source value is converted into the semantically right value of the schema node")
    private boolean transformationCorrect;

    @Description("Why, naming the wrong conversions if there are any")
    private String reason;

    @JsonIgnore
    public boolean isCorrect() {
        return mappingCorrect && transformationCorrect;
    }
}
