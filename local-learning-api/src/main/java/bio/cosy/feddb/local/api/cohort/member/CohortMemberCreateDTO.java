package bio.cosy.feddb.local.api.cohort.member;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CohortMemberCreateDTO {
    private Long cohortId;
    private String keycloakId;
    private CohortMemberTypes type;
}
