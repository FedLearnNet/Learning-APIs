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
    // What the static analysis (SAST) of the build pipeline found in the code, null for functions without code
    private String sast;
}
