package bio.cosy.feddb.local.api.query;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class QueryResultDTO {
    private Long cohortId;

    private long patientCount;

    @Override
    public String toString() {
        return "QueryResultDTO{" +
                "cohortId=" + cohortId +
                ", patientCount=" + patientCount +
                '}';
    }
}
