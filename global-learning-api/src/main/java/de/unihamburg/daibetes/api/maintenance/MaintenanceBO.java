package de.unihamburg.daibetes.api.maintenance;

import bio.cosy.feddb.core.api.maintenance.BaseMaintenanceBO;
import de.unihamburg.daibetes.execution.run.ExecutionRunAO;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class MaintenanceBO extends BaseMaintenanceBO {

    @Inject
    ExecutionRunAO runAO;

    @Override
    protected String targetVersion(String requested) {
        return requested;
    }

    @Override
    @Transactional
    protected long countOpenRunsTransactional() {
        return runAO.countOpen();
    }
}
