package bio.cosy.feddb.local.api.cohort.patient.traceability.crud;

import bio.cosy.feddb.core.base.PagedResponse;
import bio.cosy.feddb.core.base.sort.SortDirectionEnum;
import bio.cosy.feddb.local.api.auth.UserIdentity;
import bio.cosy.feddb.local.api.cohort.member.CohortMemberAuthBO;
import bio.cosy.feddb.local.api.cohort.patient.PatientAO;
import bio.cosy.feddb.local.api.cohort.patient.PatientEntity;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;

import java.util.List;
import java.util.Set;

/**
 * Implementation of the AuditService interface.
 * This class handles the REST API layer and delegates business logic to AuditBO.
 */
@ApplicationScoped
public class AuditServiceImpl implements AuditService {

    @Inject
    AuditBO auditBO;

    @Inject
    PatientAO patientAO;

    @Inject
    UserIdentity userIdentity;

    @Inject
    CohortMemberAuthBO cohortMemberAuthBO;

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
        Set<Long> cohortIds = visibleCohorts(cohortId);
        try {
            Log.debug("Querying audit logs for cohort " + cohortId + " with page " + page + ", size " + size + ", sort " + sortField + ":" + sortDirection);

            return auditBO.queryAuditLogs(
                    cohortId,
                    page,
                    size,
                    sortField,
                    sortDirection,
                    searchTerms,
                    searchFields,
                    cohortIds
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
        PatientEntity patient = patientAO.findByIdOptional(patientId)
                .orElseThrow(() -> new NotFoundException("No patient with id " + patientId + " found"));
        Long cohortId = patient.getCohort() == null ? null : patient.getCohort().getId();
        cohortMemberAuthBO.checkForMember(cohortId, userIdentity.getKeycloakId());
        return auditBO.getPatientChangesDetail(patientId, revId);
    }

    private Set<Long> visibleCohorts(Long cohortId) {
        String keycloakId = userIdentity.getKeycloakId();
        if (cohortId != null) {
            cohortMemberAuthBO.checkForMember(cohortId, keycloakId);
            return Set.of(cohortId);
        }
        return cohortMemberAuthBO.getMemberCohortIds(keycloakId);
    }
}
