package bio.cosy.feddb.local.api.cohort.patient.traceability.query;

import bio.cosy.feddb.core.base.PagedResponse;
import bio.cosy.feddb.local.api.auth.UserIdentity;
import io.quarkus.panache.common.Page;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class QueryPatientServiceImpl implements QueryPatientService {

    @Inject
    QueryPatientBO bo;

    @Inject
    UserIdentity userIdentity;

    @Override
    public PagedResponse<QueryPatientDTO> list(int pageIndex, int size, Long internalCohortId, Long internalPatientId, Long queryId) {
        Page page = Page.of(pageIndex, size);
        return bo.list(page, internalCohortId, internalPatientId, queryId);
    }
}
