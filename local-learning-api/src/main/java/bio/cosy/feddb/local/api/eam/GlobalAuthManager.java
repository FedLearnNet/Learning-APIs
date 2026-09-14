package bio.cosy.feddb.local.api.eam;

import bio.cosy.feddb.local.config.FLNetClientConfig;
import io.quarkus.logging.Log;
import io.quarkus.oidc.client.NamedOidcClient;
import io.quarkus.oidc.client.OidcClient;
import io.quarkus.oidc.client.Tokens;
import io.quarkus.oidc.client.runtime.TokensHelper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Map;
import java.util.Optional;


@ApplicationScoped
public class GlobalAuthManager {
    static final String GLOBAL_OIDC_CLIENT = "global-websocket";

    @Inject
    FLNetClientConfig config;

    @Inject
    @NamedOidcClient(GLOBAL_OIDC_CLIENT)
    OidcClient globalOidcClient;

    @Inject
    GlobalSessionLogout sessionLogout;

    TokensHelper tokensHelper = new TokensHelper();
    private String runtimeUsername;
    private String runtimePassword;
    private String userAuthorizationHeader;
    private Tokens currentTokens;
    private boolean shuttingDown;

    public boolean isEnabled() {
        return config.global().auth().enabled();
    }

    public synchronized boolean isConfigured() {
        return resolveAuthRequest().isPresent();
    }

    public Optional<String> authorizationHeader() {
        if (!isEnabled()) {
            return Optional.empty();
        }
        return Optional.of(authorization()
                .orElseThrow(() -> new IllegalStateException("Global user auth is not configured"))
                .header());
    }

    synchronized Optional<Authorization> authorization() {
        if (shuttingDown) {
            throw new IllegalStateException("Global authentication is shutting down");
        }
        if (!isEnabled()) {
            Log.debug("Global authentication is disabled");
            return Optional.empty();
        }
        Optional<AuthRequest> request = resolveAuthRequest();
        if (request.isEmpty()) {
            Log.warn("Global authentication requested but no credentials or authorization header are configured");
        }
        return request.map(authRequest -> {
            if (authRequest.authorizationHeader() != null) {
                Log.debug("Using supplied global authorization header; token acquisition and refresh are managed externally");
                return new Authorization(authRequest.authorizationHeader(), null);
            }
            boolean tokenRequestNeeded = currentTokens == null || currentTokens.isAccessTokenExpired()
                    || currentTokens.isAccessTokenWithinRefreshInterval();
            if (tokenRequestNeeded) {
                Log.infof("Acquiring or refreshing global access token: oidcClient=%s", GLOBAL_OIDC_CLIENT);
            }
            Tokens tokens;
            try {
                // Forcing new tokens performs another password grant and creates another
                // Keycloak session. Reuse the cached token and refresh the existing session.
                tokens = tokensHelper.getTokens(globalOidcClient, authRequest.grantParameters(), false)
                        .await().indefinitely();
            } catch (RuntimeException failure) {
                Log.errorf("Global access token acquisition failed: oidcClient=%s; %s; check global credentials and OIDC provider configuration/availability",
                        GLOBAL_OIDC_CLIENT, EamLogDetails.failure(failure));
                throw failure;
            }
            if (tokens != currentTokens) {
                Log.infof("Obtained new global access token: oidcClient=%s, expiresAtEpochSeconds=%s, refreshTokenAvailable=%s",
                        GLOBAL_OIDC_CLIENT, tokens.getAccessTokenExpiresAt(), tokens.getRefreshToken() != null);
                currentTokens = tokens;
            } else {
                Log.debugf("Reusing cached global access token: oidcClient=%s", GLOBAL_OIDC_CLIENT);
            }
            return new Authorization("Bearer " + tokens.getAccessToken(), tokens);
        });
    }

    public synchronized void useUserAuthorizationHeader(String authorizationHeader) {
        if (!hasText(authorizationHeader)) {
            throw new IllegalArgumentException("Authorization header must not be blank");
        }
        this.userAuthorizationHeader = authorizationHeader;
        this.runtimeUsername = null;
        this.runtimePassword = null;
        resetTokens("runtime authorization header updated");
        Log.info("Runtime global authorization header updated; cached tokens cleared; token refresh is managed externally");
    }

    public synchronized void useUserCredentials(String username, String password) {
        if (!hasText(username)) {
            throw new IllegalArgumentException("Global username must not be blank");
        }
        if (!hasText(password)) {
            throw new IllegalArgumentException("Global password must not be blank");
        }
        this.runtimeUsername = username;
        this.runtimePassword = password;
        this.userAuthorizationHeader = null;
        resetTokens("runtime credentials updated");
        Log.info("Runtime global credentials updated; cached tokens cleared; authentication will be attempted on the next token request");
    }

    public synchronized void clearUserAuth() {
        this.runtimeUsername = null;
        this.runtimePassword = null;
        this.userAuthorizationHeader = null;
        resetTokens("runtime authentication cleared");
        Log.infof("Runtime global authentication cleared; configured fallback available=%s", isConfigured());
    }

    private Optional<AuthRequest> resolveAuthRequest() {
        if (hasText(runtimeUsername) && hasText(runtimePassword)) {
            return Optional.of(AuthRequest.credentials(runtimeUsername, runtimePassword));
        }

        if (hasText(userAuthorizationHeader)) {
            return Optional.of(AuthRequest.authorizationHeader(userAuthorizationHeader));
        }

        FLNetClientConfig.AuthConfig authConfig = config.global().auth();
        if (authConfig.username().filter(GlobalAuthManager::hasText).isPresent()
                && authConfig.password().filter(GlobalAuthManager::hasText).isPresent()) {
            return Optional.of(AuthRequest.credentials(authConfig.username().get(), authConfig.password().get()));
        }

        return authConfig.authorizationHeader()
                .filter(GlobalAuthManager::hasText)
                .map(AuthRequest::authorizationHeader);
    }

    synchronized void shutdown() {
        shuttingDown = true;
        resetTokens("application shutdown");
    }

    private void resetTokens(String reason) {
        Tokens previousTokens = currentTokens;
        currentTokens = null;
        this.tokensHelper = new TokensHelper();
        if (previousTokens == null) {
            return;
        }
        String refreshToken = previousTokens.getRefreshToken();
        if (!hasText(refreshToken)) {
            Log.warnf("Cannot clean up global OIDC session: no managed refresh token; reason=%s; session must expire at the provider", reason);
            return;
        }
        try {
            sessionLogout.logout(refreshToken);
            Log.infof("Managed global Keycloak session logged out: reason=%s", reason);
        } catch (RuntimeException failure) {
            Log.warnf("Global OIDC session cleanup failed: reason=%s; %s; session may remain until provider expiry",
                    reason, EamLogDetails.failure(failure));
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    record Authorization(String header, Tokens tokens) {
    }

    private record AuthRequest(String authorizationHeader, Map<String, String> grantParameters) {

        static AuthRequest authorizationHeader(String authorizationHeader) {
            return new AuthRequest(authorizationHeader, Map.of());
        }

        static AuthRequest credentials(String username, String password) {
            return new AuthRequest(null, Map.of(
                    "username", username,
                    "password", password
            ));
        }
    }
}
