package bio.cosy.feddb.local.api.cohort.permission;

import jakarta.validation.constraints.NotNull;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@EqualsAndHashCode()
@NoArgsConstructor
@Data
public class CreatePermissionDTO {
    private String userId;
    private Integer queryRetryTime;

    private Boolean isAllowedToQuery;
    private Integer querySampleThreshold;
    private AutoTrainingAccess autoTrainingAccess;
    private AutoStatisticsAccess autoStatisticsAccess;
    private AutoMetricsAccess autoMetricsAccess;

    private LocalDate validFrom;
    private LocalDate validUntil;

    @NotNull(message = "Cohort ID cannot be null")
    private Long cohortId;
}
