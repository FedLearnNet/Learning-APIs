package de.unihamburg.daibetes.services;

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
        return users.get(0);
    }

    public String getServiceAccountToken() {
        return keycloak.tokenManager().getAccessToken().getToken();
    }

}
