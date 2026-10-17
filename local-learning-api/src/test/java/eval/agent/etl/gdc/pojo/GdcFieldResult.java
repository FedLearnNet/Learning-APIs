package eval.agent.etl.gdc.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** How the agent mapped one GDC field for one study, compared with the columns the ground truth names. */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class GdcFieldResult {
    private String study;
    private String field;
    private List<String> expected;
    private List<String> mapped;
    private String configuration;
    private GdcVerdict verdict;
    // Only for an extra: whether the LLM judge finds the mapping right all the same, and why
    private Boolean judged;
    private String judgeReason;

    public GdcFieldResult(String study, String field, List<String> expected, List<String> mapped, String configuration,
                          GdcVerdict verdict) {
        this(study, field, expected, mapped, configuration, verdict, null, null);
    }
}
