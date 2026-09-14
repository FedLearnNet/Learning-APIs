package bio.cosy.feddb.local.api.cohort.statistics.dashboard;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateCustomStatisticsDashboardDTO {
    @NotNull
    private Long cohortId;

    @NotBlank
    @Size(max = 120)
    private String name;
}
