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
}
