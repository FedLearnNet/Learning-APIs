package bio.cosy.feddb.core.security.bff;

import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.jwt.Claims;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.util.TreeSet;

@ApplicationScoped
public class BffServiceImpl implements BffService {

    @Inject
    BffConfig config;

    @Inject
    SecurityIdentity identity;

    @Override
    public Response login() {
        return redirectToFrontend();
    }

    @Override
    public Response logout() {
        return redirectToFrontend();
    }

    @Override
    public BffUserDTO me() {
        BffUserDTO user = new BffUserDTO();
        user.setUsername(identity.getPrincipal().getName());
        user.setRoles(new TreeSet<>(identity.getRoles()));
        if (identity.getPrincipal() instanceof JsonWebToken jwt) {
            user.setEmail(jwt.getClaim(Claims.email));
            user.setEmailVerified(jwt.getClaim(Claims.email_verified));
            user.setFirstName(jwt.getClaim(Claims.given_name));
            user.setLastName(jwt.getClaim(Claims.family_name));
        }
        return user;
    }

    private Response redirectToFrontend() {
        return Response.status(Response.Status.SEE_OTHER)
                .header(HttpHeaders.LOCATION, config.frontendUrl().orElse("") + "/")
                .build();
    }
}
