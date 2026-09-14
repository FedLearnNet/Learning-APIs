package bio.cosy.feddb.local.api.query;

import bio.cosy.feddb.local.api.auth.UserIdentity;
import bio.cosy.feddb.local.api.cohort.member.CohortMemberAuthBO;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;

@ApplicationScoped
public class QueryServiceImpl implements QueryService {

    @Inject
    QueryBO bo;

    @Inject
    UserIdentity userIdentity;

    @Inject
    CohortMemberAuthBO cohortMemberAuthBO;

    @Override
    public List<LocalQueryDTO> list() {
        String keycloakId = userIdentity.getKeycloakId();
        return bo.getAll().stream()
                .filter(query -> bo.getCohortIds(query.getId()).stream()
                        .allMatch(cohortId -> cohortMemberAuthBO.isMember(cohortId, keycloakId)))
                .toList();
    }

    @Override
    public LocalQueryDTO retrieve(Long id) {
        String keycloakId = userIdentity.getKeycloakId();
        bo.getCohortIds(id).forEach(cohortId -> cohortMemberAuthBO.checkForMember(cohortId, keycloakId));
        return bo.findById(id);
    }
}
