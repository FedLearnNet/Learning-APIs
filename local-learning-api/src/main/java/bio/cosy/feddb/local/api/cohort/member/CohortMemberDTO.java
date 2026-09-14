package bio.cosy.feddb.local.api.cohort.member;

import bio.cosy.feddb.core.base.BaseAuthDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class CohortMemberDTO extends BaseAuthDTO {
    private Long cohortId;
    private CohortMemberTypes type;
}
