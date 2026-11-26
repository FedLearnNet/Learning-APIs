package bio.cosy.feddb.local.api.maintenance;

import bio.cosy.feddb.core.api.maintenance.BaseMaintenanceBO;
import bio.cosy.feddb.local.api.info.InfoBO;
import bio.cosy.feddb.local.api.info.LocalInfoDTO;
import bio.cosy.feddb.local.execution.run.ExecutionRunAO;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class MaintenanceBO extends BaseMaintenanceBO {

    @Inject
    InfoBO infoBO;

    @Inject
    ExecutionRunAO runAO;

    // a client follows its platform, so only the platform version can be installed, once it is newer
    @Override
    protected String targetVersion(String requested) {
        LocalInfoDTO info = infoBO.loadInfo();
        return info.isPlatformVersionNewer() ? info.getPlatformVersion() : null;
    }

    @Override
    @Transactional
    protected long countOpenRunsTransactional() {
        return runAO.countOpen();
    }
}
