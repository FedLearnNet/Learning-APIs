package bio.cosy.feddb.local.api.cohort.statistics.entry;

import bio.cosy.feddb.local.api.auth.UserIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.core.Response;

import java.util.List;

@ApplicationScoped
public class CustomStatisticServiceImpl implements CustomStatisticService {

    @Inject
    CustomStatisticBO statisticBO;

    @Inject
    UserIdentity userIdentity;

    @Override
    @Transactional
    public List<CustomStatisticDTO> listForDashboard(Long dashboardId) {
        return statisticBO.listForDashboard(dashboardId, userIdentity.getKeycloakId());
    }

    @Override
    @Transactional
    public CustomStatisticDTO get(Long id) {
        return statisticBO.findById(id, userIdentity.getKeycloakId());
    }

    @Override
    @Transactional
    public CustomStatisticDTO create(CreateCustomStatisticDTO createDTO) {
        return statisticBO.create(createDTO, userIdentity.getKeycloakId());
    }

    @Override
    @Transactional
    public CustomStatisticDTO update(Long id, CustomStatisticDTO updateDTO) {
        return statisticBO.update(id, updateDTO, userIdentity.getKeycloakId());
    }

    @Override
    @Transactional
    public Response delete(Long id) {
        statisticBO.delete(id, userIdentity.getKeycloakId());
        return Response.ok().build();
    }
}
