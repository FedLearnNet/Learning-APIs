package bio.cosy.feddb.core.api.query;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * Relates two leaf conditions in the same Lang2Query group. Condition indexes are
 * zero-based and refer to the owning group's {@code conditions} array.
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TemporalRelationDTO {

    @JsonProperty("source_condition")
    @JsonAlias("sourceCondition")
    private Integer sourceCondition;

    @JsonProperty("target_condition")
    @JsonAlias("targetCondition")
    private Integer targetCondition;

    private String relationship;

    @JsonProperty("at_least")
    @JsonAlias("atLeast")
    private Integer atLeast;

    @JsonProperty("at_most")
    @JsonAlias("atMost")
    private Integer atMost;
}
