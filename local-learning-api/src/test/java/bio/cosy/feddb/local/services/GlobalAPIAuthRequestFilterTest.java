package bio.cosy.feddb.local.services;

import bio.cosy.feddb.local.api.eam.GlobalAuthManager;
import bio.cosy.feddb.local.services.datamodler.GlobalDataTypeService;
import bio.cosy.feddb.local.services.datamodler.GlobalOntologyService;
import bio.cosy.feddb.local.services.datamodler.GlobalSchemaService;
import bio.cosy.feddb.local.services.datamodler.GlobalSchemaSubscriptionService;
import jakarta.ws.rs.client.ClientRequestContext;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import org.eclipse.microprofile.rest.client.annotation.RegisterProvider;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalAPIAuthRequestFilterTest {

    @Test
    void leavesGlobalRequestUnauthenticatedWhenAuthIsDisabled() {
        GlobalAuthManager authManager = mock(GlobalAuthManager.class);
        when(authManager.authorizationHeader()).thenReturn(Optional.empty());
        MultivaluedMap<String, Object> headers = filter(authManager);

        assertFalse(headers.containsKey(HttpHeaders.AUTHORIZATION));
    }

    @Test
    void addsTheSharedGlobalAuthorizationHeaderWhenAuthIsEnabled() {
        GlobalAuthManager authManager = mock(GlobalAuthManager.class);
        when(authManager.authorizationHeader()).thenReturn(Optional.of("Bearer access-token"));
        MultivaluedMap<String, Object> headers = filter(authManager);

        assertEquals("Bearer access-token", headers.getFirst(HttpHeaders.AUTHORIZATION));
    }

    @Test
    void everyGlobalRestClientRegistersTheAuthenticationFilter() {
        Class<?>[] globalClients = {
                GlobalAPIService.class,
                GlobalAPIStoreService.class,
                GlobalAPIModelResultService.class,
                GitlabIssueClient.class,
                GlobalDataTypeService.class,
                GlobalOntologyService.class,
                GlobalSchemaService.class,
                GlobalSchemaSubscriptionService.class
        };

        for (Class<?> globalClient : globalClients) {
            RegisterProvider provider = globalClient.getAnnotation(RegisterProvider.class);
            assertNotNull(provider, globalClient.getName() + " must register the global auth filter");
            assertEquals(GlobalAPIAuthRequestFilter.class, provider.value());
        }
    }

    private MultivaluedMap<String, Object> filter(GlobalAuthManager authManager) {
        MultivaluedMap<String, Object> headers = new MultivaluedHashMap<>();
        ClientRequestContext requestContext = mock(ClientRequestContext.class);
        when(requestContext.getHeaders()).thenReturn(headers);

        GlobalAPIAuthRequestFilter filter = new GlobalAPIAuthRequestFilter();
        filter.globalAuthManager = authManager;
        filter.filter(requestContext);
        return headers;
    }
}
