package bio.cosy.feddb.local.api.auth;

import bio.cosy.feddb.local.services.KeycloakService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;
import org.keycloak.representations.idm.UserRepresentation;

@ApplicationScoped
public class UserBO {

    @Inject
    KeycloakService keycloakService;

    //@Inject
    //TODO if we save variabels
    //UserInfo userInfo;


    public boolean hasUserRole(String keycloakId, String role) {
        UserRepresentation user = keycloakService.getByUsername(keycloakId);
        if (user == null) {
            throw new NotFoundException("User not found");
        }

        if (user.getRealmRoles().contains(role)) {
            return true;
        }

        return false;
    }

    public boolean isSuperUser(String keycloakId) {
        return hasUserRole(keycloakId, "superuser");
    }

    public boolean isCertifier(String keycloakId) {
        return hasUserRole(keycloakId, "certifier");
    }
}
