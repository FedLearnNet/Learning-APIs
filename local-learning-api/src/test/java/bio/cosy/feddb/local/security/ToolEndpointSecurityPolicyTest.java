package bio.cosy.feddb.local.security;

import bio.cosy.feddb.core.security.ToolAuthenticated;
import bio.cosy.feddb.core.security.ToolApiKeyAuthenticationMechanism;
import bio.cosy.feddb.local.api.learning.project.run.FederatedLearningExperimentWebsocketService;
import io.quarkus.security.Authenticated;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import io.quarkus.vertx.http.runtime.security.HttpSecurityPolicy.CheckResult;
import io.smallrye.mutiny.Uni;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.ext.web.RoutingContext;
import org.junit.jupiter.api.Test;

import bio.cosy.feddb.core.security.Scope;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ToolEndpointSecurityPolicyTest {

    @Test
    void toolAnnotationRequiresAuthentication() {
        assertTrue(ToolAuthenticated.class.isAnnotationPresent(Authenticated.class));
        assertTrue(FederatedLearningExperimentWebsocketService.class
                .isAnnotationPresent(ToolAuthenticated.class));
    }

    @Test
    void allowsOnlyToolCallbacks() {
        assertGrant("POST", "/learning/run/42/upload/output", null,
                Scope.LEARNING_RUN, 42L);
        assertGrant("GET", "/patient/tool/run/42/app", "websocket",
                Scope.PATIENT_TOOL_RUN, 42L);
        assertGrant("GET", "/connectors/run/execution/42/APP", "WebSocket",
                Scope.CONNECTOR_RUN, 42L);

        assertFalse(ToolEndpointSecurityPolicy.match("GET", "/learning/run/42/download", null).isPresent());
        assertFalse(ToolEndpointSecurityPolicy.match("GET", "/cohort", null).isPresent());
        assertFalse(ToolEndpointSecurityPolicy.match("GET", "/learning/run/42/app", null).isPresent());
        assertFalse(ToolEndpointSecurityPolicy.match("GET", "/learning/run/42/controller", "websocket").isPresent());
    }

    @Test
    void runThreeIdentityCannotAccessRunTwo() {
        var identity = QuarkusSecurityIdentity.builder()
                .setPrincipal(() -> "tool-api-key:LEARNING_RUN:3")
                .addRole(ToolApiKeyAuthenticationMechanism.ROLE)
                .addAttribute(ToolApiKeyAuthenticationMechanism.SCOPE_ATTRIBUTE, Scope.LEARNING_RUN)
                .addAttribute(ToolApiKeyAuthenticationMechanism.RUN_ID_ATTRIBUTE, 3L)
                .build();

        ToolEndpointSecurityPolicy policy = new ToolEndpointSecurityPolicy();
        assertSame(CheckResult.DENY, policy.checkPermission(
                websocketContext("/learning/run/2/app"), Uni.createFrom().item(identity), null)
                .await().indefinitely());
        assertSame(CheckResult.PERMIT, policy.checkPermission(
                websocketContext("/learning/run/3/app"), Uni.createFrom().item(identity), null)
                .await().indefinitely());
    }

    private static RoutingContext websocketContext(String path) {
        RoutingContext context = mock(RoutingContext.class);
        HttpServerRequest request = mock(HttpServerRequest.class);
        when(context.request()).thenReturn(request);
        when(context.normalizedPath()).thenReturn(path);
        when(request.method()).thenReturn(HttpMethod.GET);
        when(request.getHeader("Upgrade")).thenReturn("websocket");
        return context;
    }

    private static void assertGrant(String method, String path, String upgrade,
                                    Scope scope, Long runId) {
        var grant = ToolEndpointSecurityPolicy.match(method, path, upgrade).orElseThrow();
        assertTrue(grant.scope() == scope && grant.runId().equals(runId));
    }
}
