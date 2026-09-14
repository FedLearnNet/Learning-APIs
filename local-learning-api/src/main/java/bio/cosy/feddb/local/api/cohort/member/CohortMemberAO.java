package bio.cosy.feddb.local.api.cohort.member;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

@ApplicationScoped
public class CohortMemberAO implements PanacheRepository<CohortMemberEntity> {

    public List<CohortMemberEntity> findForCohortId(Long cohortId) {
        return list("cohort.id = ?1", cohortId);
    }
}
