package eval.agent.etl.harmonization.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Objects;

import static eval.agent.etl.transform.report.TransformerWriterReport.CORRECT_SHIFT;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class HarmonizationFileResult {

    private String dataset;
    private int targets;
    // Reference columns the assistant found nothing for
    private List<String> unmapped;
    // Transformers the assistant applied, before equal ones were combined on saving
    private int suggestedTransformers;
    // Transformers of the saved harmonization
    private int transformers;
    // Rows of the output file, the reference ones included
    private int rows;
    // Source keys missing in the reference file
    private int unmatched;
    private List<HarmonizationFileColumnResult> columns;

    public int mapped() {
        return targets - unmapped.size();
    }

    public long correct() {
        return columns.stream().filter(HarmonizationFileColumnResult::correct).count();
    }

    public int rejected() {
        return columns.stream().mapToInt(HarmonizationFileColumnResult::getRejected).sum();
    }

    /** Columns whose every harmonized value is the one of the original file. */
    public long truthCorrect() {
        return columns.stream().filter(column -> column.getTruthShift() != null && column.getTruthShift() <= CORRECT_SHIFT).count();
    }

    public long truthCompared() {
        return columns.stream().filter(column -> column.getTruthShift() != null).count();
    }

    /** The mean shift of the columns that can be compared with the reference. */
    public double shift() {
        return columns.stream().map(HarmonizationFileColumnResult::getShift).filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue).average().orElse(Double.NaN);
    }
}
