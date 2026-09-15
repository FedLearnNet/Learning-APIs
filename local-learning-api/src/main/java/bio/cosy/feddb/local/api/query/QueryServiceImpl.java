package bio.cosy.feddb.local.api.query;

import bio.cosy.feddb.local.api.auth.UserIdentity;
import bio.cosy.feddb.local.api.cohort.member.CohortMemberAuthBO;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.ForbiddenException;

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
                        .anyMatch(cohortId -> cohortMemberAuthBO.isMember(cohortId, keycloakId)))
                .toList();
    }

    @Override
    public LocalQueryDTO retrieve(Long id) {
        String keycloakId = userIdentity.getKeycloakId();
        boolean allowed = bo.getCohortIds(id).stream()
                .anyMatch(cohortId -> cohortMemberAuthBO.isMember(cohortId, keycloakId));
        if (!allowed) {
            throw new ForbiddenException("You are not allowed to access this cohort");
        }
        return bo.findById(id);
    }
}
