package eval.agent.etl.gdc.setting;

import lombok.AllArgsConstructor;
import lombok.Data;

/** What tells the schema matching benchmarks apart in their results and reports. */
@Data
@AllArgsConstructor
public class MatchingBenchmark {
    private String title;
    // Name of the result files, e.g. gdc-benchmark.json
    private String file;
    private String description;
    // The schema as the judge reads it, e.g. GDC
    private String schema;
    // Whether a field is named with its table, e.g. person.gender_concept_id, as several tables have the same fields
    private boolean qualified;

    public String resultsFile() {
        return file + ".json";
    }

    public String reportFile() {
        return file + "-report.md";
    }
}
