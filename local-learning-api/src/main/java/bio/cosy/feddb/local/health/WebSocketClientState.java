package bio.cosy.feddb.local.health;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.Data;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@ApplicationScoped
@Data
public class WebSocketClientState {
    private final AtomicBoolean connected = new AtomicBoolean(false);
    private final AtomicReference<Instant> connectedSince = new AtomicReference<>(null);
    private final AtomicReference<Instant> lastMessageAt = new AtomicReference<>(null);
    private final AtomicReference<String> lastError = new AtomicReference<>(null);

    private final AtomicInteger reconnectAttempt = new AtomicInteger(0);
    private final AtomicReference<Long> nextReconnectAt = new AtomicReference<>(null);

    public void markConnected() {
        connected.set(true);
        connectedSince.compareAndSet(null, Instant.now());
        reconnectAttempt.set(0);
        nextReconnectAt.set(null);
    }

    public void markDisconnected() {
        connected.set(false);
        connectedSince.set(null);
        reconnectAttempt.set(0);
        nextReconnectAt.set(null);
    }

    public void markMessage() {
        lastMessageAt.set(Instant.now());
    }

    public void markError(String msg) {
        lastError.set(msg);
    }

    public void markReconnect(int attempt, Long nextReconnect) {
        reconnectAttempt.set(attempt);
        nextReconnectAt.set(nextReconnect);
    }

    public Long getConnectedForSeconds() {
        var since = connectedSince.get();
        return since == null ? null : Duration.between(since, Instant.now()).getSeconds();
    }
}
