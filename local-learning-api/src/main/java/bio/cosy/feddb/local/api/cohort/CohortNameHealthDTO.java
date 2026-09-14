package bio.cosy.feddb.local.api.cohort;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class CohortNameHealthDTO {
    private String name;

    @JsonProperty("nameExists")
    private boolean nameExists;
}
