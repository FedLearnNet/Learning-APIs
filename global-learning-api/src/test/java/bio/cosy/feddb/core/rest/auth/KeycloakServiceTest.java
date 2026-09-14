package bio.cosy.feddb.core.rest.auth;

import de.unihamburg.daibetes.services.KeycloakService;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import org.keycloak.representations.idm.UserRepresentation;

@Priority(1)
@Alternative
@ApplicationScoped
public class KeycloakServiceTest extends KeycloakService {


    public UserRepresentation getByUsername(String username) {
        UserRepresentation userRepresentation = new UserRepresentation();
        userRepresentation.setId(username);
        return userRepresentation;
    }
}
