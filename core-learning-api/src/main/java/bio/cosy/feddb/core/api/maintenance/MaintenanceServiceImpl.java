package bio.cosy.feddb.core.api.maintenance;

import bio.cosy.feddb.core.services.orch.dto.MaintenanceMode;
import io.smallrye.mutiny.Multi;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class MaintenanceServiceImpl implements MaintenanceService {

    // the MaintenanceBO of the API this runs in
    @Inject
    BaseMaintenanceBO maintenanceBO;

    @Override
    public Multi<MaintenanceDTO> loadMaintenance() {
        return maintenanceBO.loadMaintenance();
    }

    @Override
    public Multi<MaintenanceDTO> startMaintenance(MaintenanceMode mode, String version, boolean all, String backup) {
        return maintenanceBO.start(mode, version, all, backup);
    }
}
