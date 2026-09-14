package bio.cosy.feddb.local.api.importer.run.step;


import io.smallrye.reactive.messaging.annotations.Broadcast;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.OnOverflow;

@ApplicationScoped
public class ConnectorRunStepMessageSender {
    public static final String CONNECTOR_APP_BASED_EXECUTION_CHANNEL = "connector-app-execution-info";

    @Inject
    @Channel(CONNECTOR_APP_BASED_EXECUTION_CHANNEL)
    @OnOverflow(OnOverflow.Strategy.DROP)
    @Broadcast
    Emitter<ConnectorRunStepDTO> emitter;

    public void sendMessage(ConnectorRunStepDTO dto) {
        if (dto == null || dto.getId() == null) {
            return;
        }
        try {
            emitter.send(dto);
        } catch (Exception e) {
            //IGNORE
        }
    }
}
