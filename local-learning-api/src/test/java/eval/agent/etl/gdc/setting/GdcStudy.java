package eval.agent.etl.gdc.setting;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** A study of the GDC benchmark: its table, named like its ground truth file, and the column identifying the cases. */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class GdcStudy {
    private String name;
    private String table;
    private String caseIdColumn;

    public String dataPath() {
        return GdcStudies.ROOT + "/source-tables/" + table;
    }

    public String groundTruthPath() {
        return GdcStudies.ROOT + "/ground-truth/" + table;
    }
}
