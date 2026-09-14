package bio.cosy.feddb.local.api.cohort.patient.traceability.learning;

import bio.cosy.feddb.core.base.PagedResponse;
import bio.cosy.feddb.local.api.auth.UserIdentity;
import bio.cosy.feddb.local.api.cohort.CohortBO;
import bio.cosy.feddb.local.api.cohort.CohortDTO;
import bio.cosy.feddb.local.api.cohort.member.CohortMemberAuthBO;
import io.quarkus.panache.common.Page;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.core.Response;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@ApplicationScoped
public class PatientLearningServiceImpl implements PatientLearningService {

    @Inject
    PatientLearningBO bo;

    @Inject
    UserIdentity userIdentity;

    @Inject
    CohortMemberAuthBO cohortMemberAuthBO;

    @Inject
    CohortBO cohortBO;

    @Override
    public PagedResponse<PatientLearningDTO> list(int pageIndex, int size, Long internalCohortId, Long internalPatientId, Long requestId) {
        Page page = Page.of(pageIndex, size);
        Set<Long> accessibleCohortIds = cohortBO.getAll(userIdentity.getKeycloakId()).stream()
                .map(CohortDTO::getId)
                .collect(Collectors.toSet());
        return bo.list(page, internalCohortId, internalPatientId, requestId, accessibleCohortIds);
    }

    @Override
    @Transactional
    public Response acceptPatients(List<PatientLearningDTO> update) {
        String keycloakId = userIdentity.getKeycloakId();
        List<Long> requestIds = update.stream()
                .map(PatientLearningDTO::getRequestId)
                .distinct()
                .toList();

        Set<Long> editableCohortIds = bo.getCohortIdsForRequests(requestIds).stream()
                .filter(cohortId -> cohortMemberAuthBO.canEditPatients(cohortId, keycloakId))
                .collect(Collectors.toSet());
        if (editableCohortIds.isEmpty()) {
            throw new ForbiddenException("You are not allowed to edit patients on these cohorts");
        }

        bo.acceptPatients(update, editableCohortIds);
        return Response.ok().build();
    }
}
