package eval.agent.etl.gdc.setting;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A study of a schema matching benchmark: its table below the folder of the benchmark, named like its ground truth
 * file, and the column identifying the cases.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class GdcStudy {
    private String root;
    private String name;
    private String table;
    private String caseIdColumn;

    public GdcStudy(String name, String table, String caseIdColumn) {
        this(GdcStudies.ROOT, name, table, caseIdColumn);
    }

    public String dataPath() {
        return root + "/source-tables/" + table;
    }

    public String groundTruthPath() {
        return root + "/ground-truth/" + table;
    }
}
