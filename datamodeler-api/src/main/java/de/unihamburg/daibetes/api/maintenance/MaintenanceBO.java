package de.unihamburg.daibetes.api.maintenance;

import bio.cosy.feddb.core.api.maintenance.BaseMaintenanceBO;
import jakarta.enterprise.context.ApplicationScoped;

// not used, the datamodeler has no maintenance
@ApplicationScoped
public class MaintenanceBO extends BaseMaintenanceBO {

    @Override
    protected String targetVersion(String requested) {
        return null;
    }

    @Override
    protected long countOpenRunsTransactional() {
        return 0;
    }
}
