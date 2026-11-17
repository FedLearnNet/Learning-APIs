package bio.cosy.feddb.local.agent.etl.transform.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CandidateAssignment {
    private Long nodeId;
    private ColumnCandidate candidate;
}
