package bio.cosy.feddb.local.api.statistics;

import bio.cosy.feddb.local.api.auth.UserIdentity;
import bio.cosy.feddb.local.api.cohort.member.CohortMemberAuthBO;
import jakarta.inject.Inject;

public class DataStatisticsServiceImpl implements DataStatisticsService {

    @Inject
    DataStatisticsBO bo;

    @Inject
    UserIdentity userIdentity;

    @Inject
    CohortMemberAuthBO cohortMemberAuthBO;

    @Override
    public LocalDataStatisticsDTO getStatisticsForAll() {
        return bo.getStatisticsForAll(userIdentity.getKeycloakId());
    }

    @Override
    public LocalDataStatisticsDTO getStatisticsForCohort(Long id) {
        cohortMemberAuthBO.checkForMember(id, userIdentity.getKeycloakId());
        return bo.getStatisticsForCohort(id);
    }
}
