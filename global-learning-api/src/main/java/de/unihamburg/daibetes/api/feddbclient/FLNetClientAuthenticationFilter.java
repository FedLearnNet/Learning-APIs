package de.unihamburg.daibetes.api.feddbclient;

import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;

@Provider
@FLNetClientAuthenticated
@Priority(Priorities.AUTHORIZATION)
public class FLNetClientAuthenticationFilter implements ContainerRequestFilter {

    @Inject
    FLNetClientAuthentication authentication;

    @Override
    public void filter(ContainerRequestContext requestContext) {
        if (!authentication.isAuthorized()) {
            requestContext.abortWith(Response.status(Response.Status.UNAUTHORIZED)
                    .header(HttpHeaders.WWW_AUTHENTICATE, "Bearer")
                    .build());
        }
    }
}
