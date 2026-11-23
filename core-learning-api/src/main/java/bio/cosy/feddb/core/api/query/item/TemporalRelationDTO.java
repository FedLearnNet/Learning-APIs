package bio.cosy.feddb.core.api.query.item;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.List;

// relates two leaf conditions of the same group; indexes are zero-based into the group's conditions
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TemporalRelationDTO {
    private Integer sourceCondition;
    private Integer targetCondition;
    private TemporalRelationship relationship;
    // only for WITHIN and IN_THE_FOLLOWING
    private List<IntervalPartDTO> interval;
    private Integer atLeast;
    private Integer atMost;
}
