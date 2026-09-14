package bio.cosy.feddb.core.rest.auth;

import de.unihamburg.daibetes.api.auth.UserBO;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;

@Priority(1)
@Alternative
@ApplicationScoped
public class UserBOTest extends UserBO {


    public boolean hasUserRole(String keycloakId, String role) {
        if (keycloakId.equals("admin")) {
            return true;
        }
        return false;
    }
}
