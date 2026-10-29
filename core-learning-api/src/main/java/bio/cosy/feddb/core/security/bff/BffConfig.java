package bio.cosy.feddb.core.security.bff;

import io.smallrye.config.ConfigMapping;

import java.util.Optional;

@ConfigMapping(prefix = "flnet.bff")
public interface BffConfig {
    Optional<String> frontendUrl();

    // Externally reachable address (through nginx) for this service's own Keycloak realm, e.g.
    // http://localhost:8250/auth. quarkus.oidc.auth-server-url is the internal Docker-network
    // address the backend uses to reach Keycloak directly; the browser cannot resolve that host,
    // so OIDC redirects sent to it (login, logout) must be rewritten to this address instead.
    Optional<String> authServerPublicUrl();
}
