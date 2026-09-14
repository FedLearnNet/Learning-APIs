package bio.cosy.feddb.local.api.cohort;

import io.smallrye.common.constraint.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class CreateCohortDTO extends UpdateCohortDTO {
    @NotNull
    private String globalSchemaID;
}
