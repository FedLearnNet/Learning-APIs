package bio.cosy.feddb.local.api.importer.connector;

import bio.cosy.feddb.local.api.importer.run.ConnectorRunBO;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;

@ApplicationScoped
public class ConnectorTriggerBO {

    @Inject
    ConnectorAO connectorAO;

    @Inject
    ConnectorRunBO connectorRunBO;

    public void onConnectorSuccess(Long finishedConnectorId) {
        List<ConnectorEntity> triggered = connectorAO.findByTriggerSourceConnectorId(finishedConnectorId);
        for (ConnectorEntity entity : triggered) {
            if (entity.getTriggerType() != ConnectorTriggerTypeEnum.ON_CONNECTOR_SUCCESS) {
                continue;
            }
            Log.infof("Triggering connector %d after success of connector %d", entity.getId(), finishedConnectorId);
            connectorRunBO.startRun(entity.getId(), false, false, null);
        }
    }
}
