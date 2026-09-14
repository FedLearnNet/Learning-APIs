package bio.cosy.feddb.local.health;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;

import java.time.Instant;

@Readiness
@ApplicationScoped
public class WebSocketClientReadiness implements HealthCheck {

    @Inject
    WebSocketClientState state;

    @Override
    public HealthCheckResponse call() {
        boolean connected = state.getConnected().get();
        Instant lastMsg = state.getLastMessageAt().get();
        Instant since = state.getConnectedSince().get();
        Long connectedFor = state.getConnectedForSeconds();

        return HealthCheckResponse.named("websocket-client-readiness")
                .status(connected)
                .withData("connected", connected)
                .withData("connectedSince", since == null ? "n/a" : since.toString())
                .withData("connectedForSeconds", connectedFor == null ? "n/a" : connectedFor.toString())
                .withData("lastMessageAt", lastMsg == null ? "never" : lastMsg.toString())
                .withData("reconnectAttempt", state.getReconnectAttempt().get())
                .withData("secondsUntilNextReconnect", state.getNextReconnectAt().get() == null ? "n/a" : state.getNextReconnectAt().get().toString())
                .build();
    }
}
