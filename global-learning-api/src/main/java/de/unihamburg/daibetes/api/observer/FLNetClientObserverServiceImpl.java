package de.unihamburg.daibetes.api.observer;

import de.unihamburg.daibetes.api.feddbclient.FLNetClientBroadcastBO;
import io.smallrye.mutiny.Multi;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.reactive.messaging.Channel;

import java.util.List;

@ApplicationScoped
public class FLNetClientObserverServiceImpl implements FLNetClientObserverService {

    @Inject
    @ConfigProperty(name = "flnet.observer.enabled", defaultValue = "false")
    boolean observerEnabled;

    @Inject
    FLNetClientBroadcastBO broadcastBO;

    @Inject
    @Channel(FLNetClientObserverEmitter.CLIENT_OBSERVER_CHANNEL)
    Multi<FLNetClientObserverEventDTO> observerEvents;

    @Override
    public boolean isEnabled() {
        return observerEnabled;
    }

    @Override
    public List<String> getConnectedClients() {
        return broadcastBO.getConnections();
    }

    @Override
    public Multi<FLNetClientObserverEventDTO> streamEvents() {
        return observerEvents;
    }

    @Override
    public Response removeAllClients() {
        broadcastBO.closeAllConnections();
        return Response.ok().build();
    }
}
