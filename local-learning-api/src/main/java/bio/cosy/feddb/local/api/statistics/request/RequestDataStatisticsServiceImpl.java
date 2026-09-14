package bio.cosy.feddb.local.api.statistics.request;

import bio.cosy.feddb.core.base.PagedResponse;
import bio.cosy.feddb.local.api.auth.UserIdentity;
import bio.cosy.feddb.local.api.cohort.member.CohortMemberAuthBO;
import bio.cosy.feddb.local.api.learning.request.FederatedLearningRequestStatus;
import io.quarkus.panache.common.Page;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.core.Response;

@ApplicationScoped
public class RequestDataStatisticsServiceImpl implements RequestDataStatisticsService {

    @Inject
    RequestDataStatisticsBO bo;

    @Inject
    UserIdentity userIdentity;

    @Inject
    CohortMemberAuthBO cohortMemberAuthBO;


    @Override
    public PagedResponse<RequestDataStatisticsDTO> list(
            int pageIndex,
            int size,
            FederatedLearningRequestStatus status,
            Long patientId) {
        Page page = Page.of(pageIndex, size);
        PagedResponse<RequestDataStatisticsDTO> response = patientId == null ?
                bo.list(page, status) :
                bo.list(page, status, patientId);
        String keycloakId = userIdentity.getKeycloakId();
        response.setResults(response.getResults().stream()
                .filter(dto -> dto.getCohortIds().stream()
                        .allMatch(cohortId -> cohortMemberAuthBO.isMember(cohortId, keycloakId)))
                .toList());
        response.setTotalCount(response.getResults().size());
        return response;
    }

    @Override
    public RequestDataStatisticsDTO retrieve(Long id) {
        String keycloakId = userIdentity.getKeycloakId();
        bo.getCohortIds(id).forEach(cohortId -> cohortMemberAuthBO.checkForMember(cohortId, keycloakId));
        return bo.getById(id);
    }

    @Override
    @Transactional
    public RequestDataStatisticsDTO update(Long id, RequestDataStatisticsDTO updateDTO) {
        String keycloakId = userIdentity.getKeycloakId();
        bo.getCohortIds(id).forEach(cohortId -> cohortMemberAuthBO.checkForEditPatients(cohortId, keycloakId));
        return bo.update(id, updateDTO, keycloakId);
    }

    @Override
    public Response delete(Long id) {
        String keycloakId = userIdentity.getKeycloakId();
        bo.getCohortIds(id).forEach(cohortId -> cohortMemberAuthBO.checkForDelete(cohortId, keycloakId));
        bo.deleteById(id);
        return Response.ok().build();
    }
}
