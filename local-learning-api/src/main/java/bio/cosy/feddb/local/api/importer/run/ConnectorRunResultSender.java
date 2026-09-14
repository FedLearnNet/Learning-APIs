package bio.cosy.feddb.local.api.importer.run;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.OnOverflow;

import static bio.cosy.feddb.local.api.importer.connector.ConnectorServiceImpl.CONNECTOR_RUN_CHANNEL;

@ApplicationScoped
public class ConnectorRunResultSender {

    @Inject
    @Channel(CONNECTOR_RUN_CHANNEL)
    @OnOverflow(OnOverflow.Strategy.DROP)
    Emitter<ConnectorRunDTO> infoEmitter;

    public void sendUpdate(ConnectorRunDTO run) {
        try {
            infoEmitter.send(run);
            Log.debugf("Connector run SSE event sent for run %d, progress %d%%", run.getId(), run.getProgress());
        } catch (Exception e) {
            Log.debug("Attempted to send connector run SSE event, but client connection was already closed.", e);
        }
    }
}
