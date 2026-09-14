package bio.cosy.feddb.local.api.cohort.patient.traceability.crud;

import bio.cosy.feddb.core.base.sort.SortDirectionEnum;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AuditSortCriteriaDTO {
    private final AuditFieldEnum sortField;
    private final SortDirectionEnum sortDirection;
}
