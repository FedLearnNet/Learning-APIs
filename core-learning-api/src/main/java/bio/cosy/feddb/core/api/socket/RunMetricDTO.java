package bio.cosy.feddb.core.api.socket;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RunMetricDTO {
    private String metric;
    private String value;
    private String x;
    private String xUnit;
}
