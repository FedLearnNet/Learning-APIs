package bio.cosy.feddb.local.security;

import bio.cosy.feddb.core.security.bff.BffConfig;
import io.quarkus.arc.Unremovable;
import io.quarkus.oidc.OidcRedirectFilter;
import io.quarkus.oidc.Redirect;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.net.URI;
import java.util.Objects;
import java.util.Optional;

/**
 * Behind nginx, quarkus.oidc.auth-server-url is the internal Docker-network address of Keycloak
 * (e.g. http://keycloak:8080/auth/realms/FLNet-Client), reachable from this backend but not from
 * the browser. Rewrites any redirect Quarkus OIDC sends the browser to (login, logout) onto
 * flnet.bff.auth-server-public-url, the externally reachable origin nginx proxies back to Keycloak -
 * only the scheme/host/port are swapped, the rest of the redirect (path, query) is left untouched.
 * A no-op when either property is unset, which keeps this correct for profiles (%dev/%test) whose
 * auth-server-url is already externally reachable.
 */
@ApplicationScoped
@Unremovable
@Redirect(Redirect.Location.ALL)
public class PublicAuthServerRedirectFilter implements OidcRedirectFilter {

    @Inject
    BffConfig bffConfig;

    @ConfigProperty(name = "quarkus.oidc.auth-server-url")
    Optional<String> authServerUrl;

    @Override
    public void filter(OidcRedirectContext context) {
        Optional<String> publicUrl = bffConfig.authServerPublicUrl();
        String redirectUri = context.redirectUri();
        if (publicUrl.isEmpty() || authServerUrl.isEmpty() || redirectUri == null) {
            return;
        }
        URI redirect = URI.create(redirectUri);
        URI internal = URI.create(authServerUrl.get());
        if (!Objects.equals(redirect.getScheme(), internal.getScheme())
                || !Objects.equals(redirect.getHost(), internal.getHost())
                || redirect.getPort() != internal.getPort()) {
            return;
        }
        URI publicOrigin = URI.create(publicUrl.get());
        String portPart = publicOrigin.getPort() == -1 ? "" : ":" + publicOrigin.getPort();
        String query = redirect.getRawQuery() == null ? "" : "?" + redirect.getRawQuery();
        String rewritten = publicOrigin.getScheme() + "://" + publicOrigin.getHost() + portPart
                + redirect.getRawPath() + query;
        context.routingContext().response().headers().set("Location", rewritten);
    }
}
