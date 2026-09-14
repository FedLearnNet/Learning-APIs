package bio.cosy.feddb.local.api.learning.request;

import bio.cosy.feddb.core.base.PagedResponse;
import bio.cosy.feddb.local.api.auth.UserIdentity;
import bio.cosy.feddb.local.api.cohort.member.CohortMemberAuthBO;
import io.quarkus.panache.common.Page;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.core.Response;

@ApplicationScoped
public class FederatedLearningRequestServiceImpl implements FederatedLearningRequestService {

    @Inject
    FederatedLearningRequestBO bo;

    @Inject
    UserIdentity userIdentity;

    @Inject
    CohortMemberAuthBO cohortMemberAuthBO;


    @Override
    public PagedResponse<FederatedLearningRequestDTO> list(int pageIndex, int size, FederatedLearningRequestStatus status) {
        String keycloakId = userIdentity.getKeycloakId();
        Page page = Page.of(pageIndex, size);
        PagedResponse<FederatedLearningRequestDTO> response = bo.list(page, status, keycloakId);
        response.setResults(response.getResults());
        response.setTotalCount(response.getResults().size());
        return response;
    }

    @Override
    public FederatedLearningRequestDTO retrieve(Long id) {
        String keycloakId = userIdentity.getKeycloakId();
        bo.checkForCohortAccess(id, keycloakId);
        return bo.findByIdForUser(id, keycloakId);
    }

    @Override
    @Transactional
    public FederatedLearningRequestDTO update(Long id, FederatedLearningRequestDTO updateDTO) {
        String keycloakId = userIdentity.getKeycloakId();
        bo.checkForCohortAccess(id, keycloakId);
        return bo.update(id, updateDTO, keycloakId);
    }

    @Override
    public Response delete(Long id) {
        String keycloakId = userIdentity.getKeycloakId();
        bo.getCohortIds(id).forEach(cohortId -> cohortMemberAuthBO.checkForDelete(cohortId, keycloakId));
        bo.delete(id);
        return Response.ok().build();
    }
}
