package de.unihamburg.daibetes.api.analysis.run;

import lombok.AllArgsConstructor;
import lombok.Data;

/** An attempt or step of a run that changed; the run is sent again to whoever follows it. */
@Data
@AllArgsConstructor
public class DataAnalysisRunChange {
    private Long attemptId;
    private Long stepId;

    public static DataAnalysisRunChange ofAttempt(Long attemptId) {
        return new DataAnalysisRunChange(attemptId, null);
    }

    public static DataAnalysisRunChange ofStep(Long stepId) {
        return new DataAnalysisRunChange(null, stepId);
    }
}
