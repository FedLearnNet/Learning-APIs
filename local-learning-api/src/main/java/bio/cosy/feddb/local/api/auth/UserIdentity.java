package bio.cosy.feddb.local.api.auth;

import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Optional;
import java.util.Set;

@ApplicationScoped
public class UserIdentity {
    @Inject
    SecurityIdentity identity;

    public static final String DEFAULT_KEYCLOAK_ID = "Unknown User";

    public String getKeycloakId() {
        return identity.getPrincipal().getName();
    }

    public String getKeycloakId(String defaultValue) {
        return Optional.ofNullable(getKeycloakId()).orElseGet(() -> defaultValue);
    }

    public String getKeycloakIdOrDefault() {
        return getKeycloakId(DEFAULT_KEYCLOAK_ID);
    }

    public boolean isLoggedIn() {
        return identity != null && !identity.isAnonymous();
    }

    public Set<String> getRoles() {
        return identity.getRoles();
    }

}
