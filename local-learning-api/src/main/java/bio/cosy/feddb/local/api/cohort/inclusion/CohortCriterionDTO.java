package bio.cosy.feddb.local.api.cohort.inclusion;

import bio.cosy.feddb.core.api.query.QueryOperatorDTO;
import bio.cosy.feddb.core.base.BaseDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
public class CohortCriterionDTO extends BaseDTO {
    private CohortCriterionType type;
    private EvidenceVariableRole variableRole;
    private String description;
    private String ontologyId;
    private String dataTypeId;
    private Long cohortId;
    private List<QueryOperatorDTO> operator;
}
