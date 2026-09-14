package bio.cosy.feddb.local.api.cohort.patient.traceability.crud;

import bio.cosy.feddb.core.base.PagedResponse;
import bio.cosy.feddb.core.base.sort.SortDirectionEnum;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;

import java.util.List;

/**
 * Implementation of the AuditService interface.
 * This class handles the REST API layer and delegates business logic to AuditBO.
 */
@ApplicationScoped
public class AuditServiceImpl implements AuditService {

    @Inject
    AuditBO auditBO;

    @Override
    @Transactional
    public PagedResponse<PatientDataTraceabilityLogDto> list(
            Long cohortId,
            int page,
            int size,
            AuditFieldEnum sortField,
            SortDirectionEnum sortDirection,
            List<String> searchTerms,
            List<AuditFieldEnum> searchFields) {

        try {
            Log.debug("Querying audit logs for cohort " + cohortId + " with page " + page + ", size " + size + ", sort " + sortField + ":" + sortDirection);

            return auditBO.queryAuditLogs(
                    cohortId,
                    page,
                    size,
                    sortField,
                    sortDirection,
                    searchTerms,
                    searchFields
            );
        } catch (IllegalArgumentException e) {
            Log.warn("Bad request for audit logs query: " + e.getMessage());
            throw new BadRequestException("Invalid request parameters: " + e.getMessage(), e);
        } catch (Exception e) {
            Log.error("Unexpected error querying audit logs for cohort " + cohortId + ": " + e.getMessage(), e);
            throw e; // Let other exceptions bubble up to be handled by general exception mappers
        }
    }

    @Override
    @Transactional
    public PatientDataTraceabilityDetailLogDto getPatientChangesDetail(Long patientId, Integer revId) {
        return auditBO.getPatientChangesDetail(patientId, revId);
    }
}
