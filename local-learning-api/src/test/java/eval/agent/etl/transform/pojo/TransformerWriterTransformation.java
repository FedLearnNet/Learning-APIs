package eval.agent.etl.transform.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TransformerWriterTransformation {
    private String configuration;
    private String code;
}
