package bio.cosy.feddb.local.api.cohort.statistics.dashboard;

import bio.cosy.feddb.local.api.auth.UserIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.core.Response;

import java.util.List;

@ApplicationScoped
public class CustomStatisticsDashboardServiceImpl implements CustomStatisticsDashboardService {

    @Inject
    CustomStatisticsDashboardBO dashboardBO;

    @Inject
    UserIdentity userIdentity;

    @Override
    @Transactional
    public List<CustomStatisticsDashboardDTO> listForCohort(Long cohortId) {
        return dashboardBO.listForCohort(cohortId, userIdentity.getKeycloakId());
    }

    @Override
    @Transactional
    public CustomStatisticsDashboardDTO create(CreateCustomStatisticsDashboardDTO createDTO) {
        return dashboardBO.create(createDTO, userIdentity.getKeycloakId());
    }

    @Override
    @Transactional
    public CustomStatisticsDashboardDTO update(Long id, CustomStatisticsDashboardDTO updateDTO) {
        return dashboardBO.update(id, updateDTO, userIdentity.getKeycloakId());
    }

    @Override
    @Transactional
    public Response delete(Long id) {
        dashboardBO.delete(id, userIdentity.getKeycloakId());
        return Response.ok().build();
    }
}
