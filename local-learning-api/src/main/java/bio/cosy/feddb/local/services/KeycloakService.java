package bio.cosy.feddb.local.services;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.representations.idm.UserRepresentation;

import java.util.List;

@ApplicationScoped
public class KeycloakService {

    @Inject
    Keycloak keycloak;

    @ConfigProperty(name = "quarkus.keycloak.admin-client.realm")
    String keycloakRealm;

    public UserRepresentation getByUsername(String username) {
        List<UserRepresentation> users = keycloak.realm(keycloakRealm).users().search(username);
        if (users.isEmpty()) {
            throw new RuntimeException("User not found");
        }
        return users.getFirst();
    }

    public UserRepresentation getById(String keycloakId) {
        return keycloak.realm(keycloakRealm).users().get(keycloakId).toRepresentation();
    }

    public String getServiceAccountToken() {
        Log.debug("Get service account token");
        return keycloak.tokenManager().getAccessToken().getToken();
    }

    public List<UserRepresentation> getAvailableKeycloakUsers() {
        try {
            return keycloak.realm(keycloakRealm)
                    .users()
                    .list();
        } catch (Exception e) {
            Log.error("Failed to get available users", e);
            return List.of();
        }
    }
}
