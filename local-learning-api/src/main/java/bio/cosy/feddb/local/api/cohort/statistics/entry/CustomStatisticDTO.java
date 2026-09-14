package bio.cosy.feddb.local.api.cohort.statistics.entry;

import bio.cosy.feddb.core.base.BaseDTO;
import bio.cosy.feddb.local.api.cohort.statistics.entry.config.CustomStatisticConfig;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class CustomStatisticDTO extends BaseDTO {
    private Long dashboardId;
    private String name;
    private CustomStatisticType type;
    private CustomStatisticConfig config;
    private JsonNode data;
    private Integer sortOrder;
}
