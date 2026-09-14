package bio.cosy.feddb.local.api.eam;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.core.Response;

@ApplicationScoped
public class GlobalAuthServiceImpl implements GlobalAuthService {

    @Inject
    ClientManager clientManager;

    @Override
    public Response login(GlobalAuthLoginDTO login) {
        if (login == null || login.username() == null || login.username().isBlank()
                || login.password() == null || login.password().isBlank()) {
            Log.warn("Global login request rejected: username or password is missing");
            throw new BadRequestException("Global username and password are required");
        }

        clientManager.useUserCredentials(login.username(), login.password());
        return Response.noContent().build();
    }

    @Override
    public Response useAuthorization(String authorizationHeader) {
        if (authorizationHeader == null || authorizationHeader.isBlank()) {
            Log.warn("Global authorization update rejected: X-Global-Authorization header is missing");
            throw new BadRequestException(GlobalAuthService.GLOBAL_AUTHORIZATION_HEADER + " header is required");
        }

        clientManager.useUserAuthorizationHeader(authorizationHeader);
        return Response.noContent().build();
    }

    @Override
    public Response clearLogin() {
        clientManager.clearUserAuth();
        return Response.noContent().build();
    }
}
