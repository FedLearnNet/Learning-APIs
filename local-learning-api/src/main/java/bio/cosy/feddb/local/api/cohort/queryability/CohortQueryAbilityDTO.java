package bio.cosy.feddb.local.api.cohort.queryability;

import bio.cosy.feddb.core.base.BaseDTO;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@Data
public class CohortQueryAbilityDTO extends BaseDTO {

    @NotNull(message = "Schema node ID cannot be null")
    private Long schemaNodeId;

    @NotNull(message = "Query ability info cannot be null")
    private QueryAbility queryAbilityInfo = QueryAbility.VALUE;

    @NotNull(message = "Cohort ID cannot be null")
    private Long cohortId;
}
