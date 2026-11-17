package eval.agent.etl.harmonization.pojo;

import eval.agent.etl.transform.judge.TransformerWriterVerdict;
import eval.agent.etl.transform.pojo.TransformerWriterReview;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class HarmonizationFileColumnResult {

    private String column;
    private TransformerWriterReview review;
    private TransformerWriterVerdict verdict;
    // How far the harmonized values are from the reference ones
    private Double shift;
    // Share of the harmonized rows without a value
    private double empty;
    // Share of the reference rows without a value
    private double referenceEmpty;
    // Harmonized values the data type of the reference column rejected, left empty
    private int rejected;
    // Share of the rows whose harmonized value differs from the original file, see TransformerWriterTruth
    private Double truthShift;

    public HarmonizationFileColumnResult judged(TransformerWriterVerdict verdict) {
        return new HarmonizationFileColumnResult(column, review, verdict, shift, empty, referenceEmpty, rejected, truthShift);
    }

    public boolean correct() {
        return verdict != null && verdict.isCorrect();
    }
}
