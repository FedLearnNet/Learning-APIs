package bio.cosy.feddb.local.api.cohort.statistics.dashboard;

import bio.cosy.feddb.core.base.BaseDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class CustomStatisticsDashboardDTO extends BaseDTO {
    private Long cohortId;
    private String name;
    private Integer sortOrder;
}
