package bio.cosy.feddb.local.api.cohort;

import bio.cosy.feddb.local.api.cohort.member.CohortMemberDTO;
import bio.cosy.feddb.local.api.schema.LocalSchemaRootNodeDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
public class CohortDetailDTO extends CohortDTO {
    private LocalSchemaRootNodeDTO schemaRoot;
    private List<CohortMemberDTO> members;
}
