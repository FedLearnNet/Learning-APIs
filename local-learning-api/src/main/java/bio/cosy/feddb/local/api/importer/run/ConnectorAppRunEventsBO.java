package bio.cosy.feddb.local.api.importer.run;

import bio.cosy.feddb.core.execution.event.RunFinishedEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.TransactionPhase;
import jakarta.inject.Inject;

@ApplicationScoped
public class ConnectorAppRunEventsBO {

    @Inject
    ConnectorAppRunBO connectorAppRunBO;

    void onRunFinished(@Observes(during = TransactionPhase.AFTER_SUCCESS) RunFinishedEvent event) {
        connectorAppRunBO.forgetSession(event.getRunId());
    }
}
