package bio.cosy.feddb.local.health;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Liveness;

@Liveness
@ApplicationScoped
public class WebSocketClientLiveness implements HealthCheck {

    @Inject
    WebSocketClientState state;

    @Override
    public HealthCheckResponse call() {
        return HealthCheckResponse.named("websocket-client-liveness")
                .status(true)
                .withData("connected", state.getConnected().get())
                .withData("connectedSince",
                        state.getConnectedSince() == null ? "n/a" : state.getConnectedSince().toString())
                .withData("lastError",
                        state.getLastError() == null ? "none" : state.getLastError().get())
                .withData("reconnectAttempt", state.getReconnectAttempt() == null ? 0 : state.getReconnectAttempt().get())
                .withData("secondsUntilNextReconnect", state.getNextReconnectAt().get() == null ? "n/a" : state.getNextReconnectAt().get().toString())
                .build();
    }
}
