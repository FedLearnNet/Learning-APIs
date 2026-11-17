package bio.cosy.feddb.local.api.cohort.patient.traceability.query;

import bio.cosy.feddb.core.base.PagedResponse;
import bio.cosy.feddb.local.api.auth.UserIdentity;
import bio.cosy.feddb.local.api.cohort.member.CohortMemberAuthBO;
import io.quarkus.panache.common.Page;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Set;

@ApplicationScoped
public class QueryPatientServiceImpl implements QueryPatientService {

    @Inject
    QueryPatientBO bo;

    @Inject
    UserIdentity userIdentity;

    @Inject
    CohortMemberAuthBO cohortMemberAuthBO;

    @Override
    public PagedResponse<QueryPatientDTO> list(int pageIndex, int size, Long internalCohortId, Long internalPatientId, Long queryId) {
        Page page = Page.of(pageIndex, size);
        String keycloakId = userIdentity.getKeycloakId();
        Set<Long> cohortIds;
        if (internalCohortId != null) {
            cohortMemberAuthBO.checkForMember(internalCohortId, keycloakId);
            cohortIds = Set.of(internalCohortId);
        } else {
            cohortIds = cohortMemberAuthBO.memberCohortIdsOf(keycloakId);
        }
        return bo.list(page, internalCohortId, internalPatientId, queryId, cohortIds);
    }
}
