package bio.cosy.feddb.local.services;

import bio.cosy.feddb.local.api.eam.GlobalAuthManager;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.client.ClientRequestContext;
import jakarta.ws.rs.client.ClientRequestFilter;
import jakarta.ws.rs.core.HttpHeaders;

@ApplicationScoped
@Priority(Priorities.AUTHENTICATION)
public class GlobalAPIAuthRequestFilter implements ClientRequestFilter {

    @Inject
    GlobalAuthManager globalAuthManager;

    @Override
    public void filter(ClientRequestContext requestContext) {
        globalAuthManager.authorizationHeader()
                .ifPresent(header -> requestContext.getHeaders().putSingle(HttpHeaders.AUTHORIZATION, header));
    }
}
