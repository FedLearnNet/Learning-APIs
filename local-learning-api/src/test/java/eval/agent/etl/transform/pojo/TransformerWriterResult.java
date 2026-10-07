package eval.agent.etl.transform.pojo;

import eval.agent.etl.transform.judge.TransformerWriterVerdict;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TransformerWriterResult {
    private String dataset;
    private TransformerWriterReview review;
    private TransformerWriterVerdict verdict;
}
