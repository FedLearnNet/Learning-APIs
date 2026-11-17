package bio.cosy.feddb.local.api.cohort;

import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class CreateCohortDTO extends UpdateCohortDTO {
    // null when the cohort links an external database
    private String globalSchemaID;
}
