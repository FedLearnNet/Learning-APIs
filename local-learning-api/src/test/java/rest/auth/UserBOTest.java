package rest.auth;

import bio.cosy.feddb.local.api.auth.UserBO;
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
