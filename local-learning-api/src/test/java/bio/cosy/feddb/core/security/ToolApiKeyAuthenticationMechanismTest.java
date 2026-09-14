package bio.cosy.feddb.core.security;

import io.quarkus.security.identity.SecurityIdentity;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.ext.web.RoutingContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ToolApiKeyAuthenticationMechanismTest {

    @Test
    void authenticatesAValidRunScopedApiKey() {
        ToolApiKeyService apiKeys = new ToolApiKeyService();
        String apiKey = apiKeys.issue(Scope.LEARNING_RUN, 42L);
        ToolApiKeyAuthenticationMechanism mechanism = new ToolApiKeyAuthenticationMechanism();
        mechanism.apiKeys = apiKeys;

        SecurityIdentity identity = mechanism.authenticate(contextWithApiKey(apiKey), null)
                .await().indefinitely();

        assertTrue(identity.hasRole(ToolApiKeyAuthenticationMechanism.ROLE));
        assertEquals(Scope.LEARNING_RUN,
                identity.getAttribute(ToolApiKeyAuthenticationMechanism.SCOPE_ATTRIBUTE));
        assertEquals(Long.valueOf(42),
                identity.<Long>getAttribute(ToolApiKeyAuthenticationMechanism.RUN_ID_ATTRIBUTE));
    }

    @Test
    void doesNotAuthenticateAnInvalidApiKey() {
        ToolApiKeyAuthenticationMechanism mechanism = new ToolApiKeyAuthenticationMechanism();
        mechanism.apiKeys = new ToolApiKeyService();

        assertNull(mechanism.authenticate(contextWithApiKey("invalid"), null)
                .await().indefinitely());
    }

    private static RoutingContext contextWithApiKey(String apiKey) {
        RoutingContext context = mock(RoutingContext.class);
        HttpServerRequest request = mock(HttpServerRequest.class);
        when(context.request()).thenReturn(request);
        when(request.getHeader(ToolApiKeyService.HEADER_NAME)).thenReturn(apiKey);
        return context;
    }
}
