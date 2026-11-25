package bio.cosy.feddb.core.api.query.item;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// one part of an interval such as "2 weeks 3 days"
@Data
@NoArgsConstructor
@AllArgsConstructor
public class IntervalPartDTO {
    private Integer count;
    private IntervalUnit unit;
}
