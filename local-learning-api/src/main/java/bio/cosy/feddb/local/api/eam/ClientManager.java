package bio.cosy.feddb.local.api.eam;

import bio.cosy.feddb.core.api.socket.FedDBClientDataDTO;
import bio.cosy.feddb.local.config.FLNetClientConfig;
import bio.cosy.feddb.local.health.WebSocketClientState;
import io.quarkus.logging.Log;
import io.quarkus.oidc.client.Tokens;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import io.quarkus.websockets.next.Closed;
import io.quarkus.websockets.next.WebSocketClientConnection;
import io.quarkus.websockets.next.WebSocketConnector;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.time.Instant;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@ApplicationScoped
public class ClientManager {
    private static final String AUTHORIZATION_HEADER = "Authorization";

    @Inject
    FLNetClientConfig config;

    @Inject
    Instance<WebSocketConnector<WebsocketClient>> connectors;

    @Inject
    WebSocketClientState state;

    @Inject
    GlobalAuthManager globalAuthManager;

    private final Queue<FedDBClientDataDTO<?>> pendingClientMessages = new ConcurrentLinkedQueue<>();
    private WebSocketClientConnection connection;
    private ScheduledExecutorService reconnectExecutor;
    private ScheduledFuture<?> reconnectTask;
    private ScheduledExecutorService tokenRefreshExecutor;
    private ScheduledFuture<?> tokenRefreshTask;
    private boolean isReconnecting = false;
    private int reconnectAttempt = 0;
    private volatile boolean shuttingDown;
    void connect() {
        if (shuttingDown) {
            return;
        }
        FLNetClientConfig.SocketConfig socketConfig = config.global().socket();
        if (!socketConfig.enabled()) {
            Log.warn("\n" +
                    "#####################################################\n" +
                    "###  WARNING: Socket connection is DISABLED       ###\n" +
                    "###  No Global connection will be established.    ###\n" +
                    "###  You can interact with the system only LOCAL. ###\n" +
                    "#####################################################");
            return;
        }
        String connectionPhase = "configuration";
        try {
            Log.info("Connecting to " + socketConfig.uri());
            if (connection != null && connection.isOpen()) {
                state.markConnected();
                Log.info("Connection already open");
                return;
            }
            boolean authEnabled = isAuthEnabled();
            if (authEnabled && !globalAuthManager.isConfigured()) {
                state.markDisconnected();
                state.markError("Global WebSocket user auth is not configured");
                Log.warn("Global WebSocket user auth is not configured; waiting for /global/auth/login or flnet.global.auth.username/password");
                return;
            }

            connectionPhase = "authentication";
            Optional<GlobalAuthManager.Authorization> authorization = globalAuthManager.authorization();
            Tokens tokens = authorization.map(GlobalAuthManager.Authorization::tokens).orElse(null);
            String authorizationHeader = authorization.map(GlobalAuthManager.Authorization::header).orElse(null);
            Log.debugf("Global authorization header configured: %s", authorizationHeader != null);
            connectionPhase = "opening handshake";
            Log.infof("Opening global WebSocket handshake: authenticationEnabled=%s, authorizationHeaderPresent=%s",
                    authEnabled, authorizationHeader != null);
            WebSocketConnector<WebsocketClient> connector = connectors.get()
                    .baseUri(socketConfig.uri());
            if (authorizationHeader != null) {
                connector.addHeader(AUTHORIZATION_HEADER, authorizationHeader);
            }
            connection = connector.connectAndAwait();
            if (shuttingDown) {
                closeConnection("opening handshake completed during shutdown");
                return;
            }
            Log.infof("Global WebSocket handshake successful: connectionId=%s", connection.id());
            connectionPhase = "token rollover scheduling";
            if (tokens != null) {
                scheduleTokenRefresh(tokens);
            } else {
                cancelTokenRefresh();
            }
        } catch (Exception e) {
            Log.errorf("Global WebSocket connection failed: phase=%s; %s", connectionPhase, EamLogDetails.failure(e));
            state.markError(e.getMessage());
            state.markDisconnected();
            this.connection = null;
            if (!isReconnecting) {
                reconnect("connection failed during " + connectionPhase);
            }
        }
    }

    void reconnect() {
        reconnect("reconnect requested");
    }

    private void reconnect(String reason) {
        if (shuttingDown) {
            Log.debugf("Skipping global WebSocket reconnect during shutdown: reason=%s", reason);
            return;
        }
        if (isAuthEnabled() && !globalAuthManager.isConfigured()) {
            closeConnection("global authentication is not configured");
            state.markDisconnected();
            state.markError("Global WebSocket user auth is not configured");
            Log.warn("Global WebSocket reconnect skipped because no global user auth is configured");
            cleanupReconnectExecutor();
            return;
        }

        FLNetClientConfig.ReconnectConfig reconnectConfig = config.global().socket().reconnect();
        cancelTokenRefresh();
        if (reconnectTask != null && !reconnectTask.isDone()) {
            Log.debugf("Global WebSocket reconnect already scheduled; additional trigger=%s", reason);
            return;
        }
        if (reconnectExecutor == null) {
            reconnectExecutor = Executors.newSingleThreadScheduledExecutor();
        }
        if (!isReconnecting) {
            reconnectAttempt = 0;
            isReconnecting = true;
        }
        long delay = (long) Math.min(
                reconnectConfig.delay().init() * Math.pow(2, reconnectAttempt),
                reconnectConfig.delay().reconnect()
        );

        reconnectAttempt++;

        state.markReconnect(reconnectAttempt, delay);
        Log.infof("Scheduling global WebSocket reconnect: attempt=%d, delayMs=%d, reason=%s",
                reconnectAttempt, delay, reason);

        reconnectTask = reconnectExecutor.schedule(() -> {
            try {
                Log.infof("Attempting global WebSocket reconnect: attempt=%d, reason=%s", reconnectAttempt, reason);
                closeConnection(reason);
                this.connect();
                if (connection != null && connection.isOpen()) {
                    isReconnecting = false;
                    reconnectAttempt = 0;
                    Log.info("Reconnect successful");
                    cleanupReconnectExecutor();
                    return;
                }
                throw new RuntimeException("Connection not open");
            } catch (Exception e) {
                Log.errorf("Global WebSocket reconnect failed: attempt=%d; %s", reconnectAttempt, EamLogDetails.failure(e));
                if (reconnectAttempt < reconnectConfig.attempts() || reconnectConfig.attempts() == -1) {
                    reconnectTask = null;
                    reconnect(reason);
                } else {
                    Log.error("Max reconnect attempts reached. Stopping reconnection.");
                    isReconnecting = false;
                    reconnectAttempt = 0;
                    cleanupReconnectExecutor();
                }
            }
        }, delay, TimeUnit.MILLISECONDS);
    }


    private void scheduleTokenRefresh(Tokens tokens) {
        Long expiresAt = tokens.getAccessTokenExpiresAt();
        if (expiresAt == null) {
            Log.warn("Access token expiration is not available; token rollover reconnect is not scheduled");
            return;
        }
        if (tokenRefreshExecutor == null) {
            tokenRefreshExecutor = Executors.newSingleThreadScheduledExecutor();
        }

        cancelTokenRefresh();
        long refreshSkewSeconds = tokens.getRefreshTokenTimeSkew() != null
                ? tokens.getRefreshTokenTimeSkew()
                : 0;
        long now = Instant.now().getEpochSecond();
        long delaySeconds = Math.max(1, expiresAt - now - refreshSkewSeconds + 1);

        Log.infof("Scheduling WebSocket token rollover reconnect: delaySeconds=%d, expiresAtEpochSeconds=%d, refreshSkewSeconds=%d",
                delaySeconds, expiresAt, refreshSkewSeconds);
        tokenRefreshTask = tokenRefreshExecutor.schedule(() -> {
            Log.info("Refreshing global WebSocket authentication with a fresh opening handshake");
            reconnect("access token rollover before expiration");
        }, delaySeconds, TimeUnit.SECONDS);
    }

    private void cancelTokenRefresh() {
        if (tokenRefreshTask != null) {
            tokenRefreshTask.cancel(false);
            tokenRefreshTask = null;
        }
    }

    private void cleanupReconnectExecutor() {
        reconnectTask = null;
        if (reconnectExecutor != null) {
            reconnectExecutor.shutdown();
            reconnectExecutor = null;
        }
    }

    public void connect(@Observes StartupEvent event) {
        this.connect();
    }

    public void shutdown(@Observes ShutdownEvent event) {
        shuttingDown = true;
        Log.infof("Shutting down global WebSocket client: connectionId=%s; cancelling reconnect and token rollover tasks",
                connection != null ? connection.id() : "none");
        cancelTokenRefresh();
        if (tokenRefreshExecutor != null) {
            tokenRefreshExecutor.shutdownNow();
            tokenRefreshExecutor = null;
        }
        if (reconnectTask != null) {
            reconnectTask.cancel(false);
        }
        cleanupReconnectExecutor();
        try {
            closeConnection("application shutdown");
        } finally {
            globalAuthManager.shutdown();
        }
    }

    public void connectionClosed(@ObservesAsync @Closed WebSocketClientConnection connection) {
        Log.debugf("Global WebSocket close event received: connectionId=%s", connection.id());
        reconnect("connection closed: connectionId=" + connection.id());
    }

    public void fireAndForget(FedDBClientDataDTO<?> dto) {
        this.fireAndForget(dto, this.connection);
    }

    public void fireAndForget(FedDBClientDataDTO<?> dto, WebSocketClientConnection connection) {
        //Fire and forget
        Log.infof("Sending message: %s", dto);
        if (connection != null && connection.isOpen()) {
            Log.infof("Websocket connection is open, sending message: %s", dto);
            connection.sendText(dto)
                    .emitOn(Infrastructure.getDefaultWorkerPool())
                    .await().indefinitely();
        } else {
            Log.errorf("Websocket connection is not open for sending message: %s", dto);
            pendingClientMessages.add(dto);
            Log.debugf("Message queued: %s", dto);
        }
    }

    public void connectionOpened(WebSocketClientConnection connection) {
        Log.infof("Global WebSocket ready: connectionId=%s, queuedMessages=%d; resetting reconnect attempt",
                connection.id(), pendingClientMessages.size());

        isReconnecting = false;
        reconnectAttempt = 0;

        if (pendingClientMessages.isEmpty()) {
            Log.info("No messages queued");
            return;
        }

        processQueuedMessages(connection);
    }

    public void useUserAuthorizationHeader(String authorizationHeader) {
        globalAuthManager.useUserAuthorizationHeader(authorizationHeader);
        reconnect("runtime authorization header updated");
    }

    public void useUserCredentials(String username, String password) {
        globalAuthManager.useUserCredentials(username, password);
        reconnect("runtime credentials updated");
    }

    public void clearUserAuth() {
        globalAuthManager.clearUserAuth();
        if (globalAuthManager.isConfigured()) {
            reconnect("runtime authentication cleared; using configured fallback");
        } else {
            cancelTokenRefresh();
            closeConnection("runtime authentication cleared; no configured fallback");
            state.markDisconnected();
        }
    }

    private void closeConnection(String reason) {
        if (connection != null && connection.isOpen()) {
            Log.infof("Closing global WebSocket locally: connectionId=%s, reason=%s", connection.id(), reason);
            connection.close().await().atMost(Duration.ofSeconds(5));
        }
        connection = null;
    }

    private boolean isAuthEnabled() {
        return globalAuthManager.isEnabled();
    }

    private void processQueuedMessages(WebSocketClientConnection connection) {
        Log.info("Processing queued messages");

        // Create a copy of the queue to avoid concurrent modification
        List<FedDBClientDataDTO<?>> messagesToProcess = new ArrayList<>(pendingClientMessages);
        pendingClientMessages.clear();

        for (FedDBClientDataDTO<?> dto : messagesToProcess) {
            fireAndForget(dto, connection);
        }
    }

}
