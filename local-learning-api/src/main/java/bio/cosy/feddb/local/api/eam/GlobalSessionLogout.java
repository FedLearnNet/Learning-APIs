package bio.cosy.feddb.local.api.eam;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;

@ApplicationScoped
public class GlobalSessionLogout {
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    @ConfigProperty(name = "quarkus.oidc-client.global-websocket.auth-server-url")
    Optional<URI> authServerUrl;

    @ConfigProperty(name = "quarkus.oidc-client.global-websocket.client-id")
    Optional<String> clientId;

    @ConfigProperty(name = "quarkus.oidc-client.global-websocket.credentials.secret")
    Optional<String> clientSecret;

    void logout(String refreshToken) {
        HttpRequest request = logoutRequest(refreshToken);
        try (HttpClient client = HttpClient.newBuilder().connectTimeout(TIMEOUT).build()) {
            HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() != 204 && response.statusCode() != 200) {
                Log.warnf("Global Keycloak session logout rejected: httpStatus=%d", response.statusCode());
                throw new IllegalStateException("Global session logout failed with HTTP " + response.statusCode());
            }
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Global session logout interrupted", failure);
        } catch (IOException failure) {
            throw new IllegalStateException("Global session logout failed", failure);
        }
    }

    HttpRequest logoutRequest(String refreshToken) {
        String realmUrl = authServerUrl.orElseThrow(() -> new IllegalStateException("Global auth server URL is missing"))
                .toString().replaceAll("/+$", "");
        String form = "client_id=" + encode(clientId.orElseThrow(() -> new IllegalStateException("Global client ID is missing")))
                + "&refresh_token=" + encode(refreshToken);
        if (clientSecret.isPresent()) {
            form += "&client_secret=" + encode(clientSecret.get());
        }
        // Keycloak's refresh-token logout targets this session. Do not use the
        // revocation endpoint: older versions revoke every session for the shared client.
        return HttpRequest.newBuilder(URI.create(realmUrl + "/protocol/openid-connect/logout"))
                .timeout(TIMEOUT)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
