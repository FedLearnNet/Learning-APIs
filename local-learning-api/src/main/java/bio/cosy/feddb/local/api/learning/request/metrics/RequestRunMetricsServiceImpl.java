package bio.cosy.feddb.local.api.learning.request.metrics;

import bio.cosy.feddb.core.base.PagedResponse;
import bio.cosy.feddb.local.api.auth.UserIdentity;
import bio.cosy.feddb.local.api.cohort.member.CohortMemberAuthBO;
import bio.cosy.feddb.local.api.learning.request.FederatedLearningRequestStatus;
import io.quarkus.panache.common.Page;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class RequestRunMetricsServiceImpl implements RequestRunMetricsService {

    @Inject
    RequestRunMetricsBO bo;

    @Inject
    UserIdentity userIdentity;

    @Inject
    CohortMemberAuthBO cohortMemberAuthBO;

    @Override
    public PagedResponse<RequestRunMetricsDTO> list(int page, int size, FederatedLearningRequestStatus status) {
        PagedResponse<RequestRunMetricsDTO> response = bo.list(Page.of(page, size), status);
        String keycloakId = userIdentity.getKeycloakId();
        response.setResults(response.getResults().stream()
                .filter(dto -> bo.getCohortIds(dto.getId()).stream()
                        .allMatch(cohortId -> cohortMemberAuthBO.isMember(cohortId, keycloakId)))
                .toList());
        response.setTotalCount(response.getResults().size());
        return response;
    }

    @Override
    public RequestRunMetricsDTO retrieve(Long id) {
        String keycloakId = userIdentity.getKeycloakId();
        bo.getCohortIds(id).forEach(cohortId -> cohortMemberAuthBO.checkForMember(cohortId, keycloakId));
        return bo.getById(id);
    }

    @Override
    public RequestRunMetricsDTO update(Long id, RequestRunMetricsDTO updateDTO) {
        String keycloakId = userIdentity.getKeycloakId();
        bo.getCohortIds(id).forEach(cohortId -> cohortMemberAuthBO.checkForEditPatients(cohortId, keycloakId));
        return bo.update(id, updateDTO, keycloakId);
    }
}
