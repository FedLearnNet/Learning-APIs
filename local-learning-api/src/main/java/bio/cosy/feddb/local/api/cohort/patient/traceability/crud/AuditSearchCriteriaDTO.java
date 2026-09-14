package bio.cosy.feddb.local.api.cohort.patient.traceability.crud;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AuditSearchCriteriaDTO {
    private final String searchTerm;
    private final AuditFieldEnum searchType;
}
