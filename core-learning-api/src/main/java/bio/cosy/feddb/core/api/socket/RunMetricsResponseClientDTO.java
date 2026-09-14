package bio.cosy.feddb.core.api.socket;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class RunMetricsResponseClientDTO {
    private String globalExperimentUniqueId;
    private String randomClinicId;
    private UUID requestId;
    private List<RunMetricDTO> metrics;
}
