package bio.cosy.feddb.local.api.importer.connector;

import bio.cosy.feddb.local.api.auth.UserIdentity;
import bio.cosy.feddb.local.api.importer.run.ConnectorRunBO;
import io.quarkus.logging.Log;
import io.quarkus.scheduler.Scheduler;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.List;

@ApplicationScoped
public class ConnectorScheduleBO {

    private static final String JOB_PREFIX = "connector-schedule-";

    @Inject
    Scheduler scheduler;

    @Inject
    ConnectorAO connectorAO;

    @Inject
    ConnectorRunBO connectorRunBO;

    @Transactional
    public void initTransactional() {
        Log.info("Initializing ConnectorScheduleBO, registering scheduled connector jobs...");
        List<ConnectorEntity> scheduled = connectorAO.findAllWithSchedule();
        for (ConnectorEntity entity : scheduled) {
            if (entity.getScheduleSettings() == null
                    || entity.getScheduleSettings().getEnabled().equals(Boolean.FALSE)
                    || entity.getScheduleSettings().getCronExpression() == null
                    || entity.getScheduleSettings().getCronExpression().isBlank()) {
                continue;
            }
            registerJob(entity.getId(), entity.getScheduleSettings().getCronExpression());
        }
        Log.infof("Registered %d connector schedules", scheduled.size());
    }

    public void syncSchedule(Long connectorId, ScheduleSettingsDTO newSettings) {
        String jobIdentity = JOB_PREFIX + connectorId;
        scheduler.unscheduleJob(jobIdentity);

        if (newSettings != null && newSettings.getCronExpression() != null && !newSettings.getCronExpression().isBlank()) {
            registerJob(connectorId, newSettings.getCronExpression());
            Log.infof("Scheduled connector %d with cron: %s", connectorId, newSettings.getCronExpression());
        } else {
            Log.infof("Removed schedule for connector %d", connectorId);
        }
    }

    public void removeSchedule(Long connectorId) {
        scheduler.unscheduleJob(JOB_PREFIX + connectorId);
    }

    private void registerJob(Long connectorId, String cron) {
        scheduler.newJob(JOB_PREFIX + connectorId)
                .setCron(cron)
                .setTask(executionContext -> {
                    Log.infof("Scheduled run triggered for connector %d", connectorId);
                    connectorRunBO.startRun(connectorId, false, false, UserIdentity.DEFAULT_KEYCLOAK_ID);
                })
                .schedule();
    }
}
