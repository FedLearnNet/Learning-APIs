package bio.cosy.feddb.local.security;

import bio.cosy.feddb.core.security.ToolAuthenticated;
import bio.cosy.feddb.core.security.ToolApiKeyAuthenticationMechanism;
import bio.cosy.feddb.local.execution.attempt.ExecutionAttemptWebsocketService;
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
        assertTrue(ExecutionAttemptWebsocketService.class
                .isAnnotationPresent(ToolAuthenticated.class));
    }

    @Test
    void allowsOnlyToolCallbacks() {
        assertGrant("POST", "/execution/APP/42/upload/output", null, Scope.EXECUTION_ATTEMPT, 42L);
        assertGrant("POST", "/execution/APP/42/upload/model", null, Scope.EXECUTION_ATTEMPT, 42L);
        assertGrant("GET", "/execution/APP/42/app", "websocket", Scope.EXECUTION_ATTEMPT, 42L);
        assertGrant("POST", "/execution/learning/42/upload/output", null,
                Scope.LEARNING_RUN, 42L);
        assertGrant("POST", "/execution/learning/42/upload/model", null,
                Scope.LEARNING_RUN, 42L);
        assertGrant("GET", "/execution/tool/42/app", "websocket",
                Scope.TOOL_RUN, 42L);
        assertGrant("GET", "/execution/tool/42/APP", "WebSocket",
                Scope.TOOL_RUN, 42L);

        assertFalse(ToolEndpointSecurityPolicy.match("GET", "/execution/learning/42/download", null).isPresent());
        assertFalse(ToolEndpointSecurityPolicy.match("GET", "/cohort", null).isPresent());
        assertFalse(ToolEndpointSecurityPolicy.match("GET", "/execution/learning/42/app", null).isPresent());
        assertFalse(ToolEndpointSecurityPolicy.match("GET", "/execution/learning/42/controller", "websocket").isPresent());
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
                websocketContext("/execution/learning/2/app"), Uni.createFrom().item(identity), null)
                .await().indefinitely());
        assertSame(CheckResult.PERMIT, policy.checkPermission(
                websocketContext("/execution/learning/3/app"), Uni.createFrom().item(identity), null)
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

    @Test
    void canonicalCallbacksRejectUsersAndKeysForAnotherExecution() {
        ToolEndpointSecurityPolicy policy = new ToolEndpointSecurityPolicy();
        var key = io.quarkus.security.runtime.QuarkusSecurityIdentity.builder()
                .setPrincipal(() -> "execution-42")
                .addRole(bio.cosy.feddb.core.security.ToolApiKeyAuthenticationMechanism.ROLE)
                .addAttribute(bio.cosy.feddb.core.security.ToolApiKeyAuthenticationMechanism.SCOPE_ATTRIBUTE, Scope.EXECUTION_ATTEMPT)
                .addAttribute(bio.cosy.feddb.core.security.ToolApiKeyAuthenticationMechanism.RUN_ID_ATTRIBUTE, 42L).build();
        var user = io.quarkus.security.runtime.QuarkusSecurityIdentity.builder()
                .setPrincipal(() -> "owner").build();
        var anonymous = io.quarkus.security.runtime.QuarkusSecurityIdentity.builder().setAnonymous(true).build();
        for (var identity : java.util.List.of(key, user, anonymous)) {
            for (String path : java.util.List.of("/execution/APP/42/upload/output", "/execution/APP/43/upload/output",
                    "/execution/APP/42/credential")) {
                var context = org.mockito.Mockito.mock(io.vertx.ext.web.RoutingContext.class);
                var request = org.mockito.Mockito.mock(io.vertx.core.http.HttpServerRequest.class);
                org.mockito.Mockito.when(context.request()).thenReturn(request);
                org.mockito.Mockito.when(context.normalizedPath()).thenReturn(path);
                org.mockito.Mockito.when(request.method()).thenReturn(io.vertx.core.http.HttpMethod.POST);
                var result = policy.checkPermission(context, io.smallrye.mutiny.Uni.createFrom().item(identity), null)
                        .await().indefinitely();
                boolean allowed = (identity == key && path.equals("/execution/APP/42/upload/output"))
                        || (identity != key && path.endsWith("/credential"));
                org.junit.jupiter.api.Assertions.assertSame(allowed
                        ? io.quarkus.vertx.http.runtime.security.HttpSecurityPolicy.CheckResult.PERMIT
                        : io.quarkus.vertx.http.runtime.security.HttpSecurityPolicy.CheckResult.DENY, result);
            }
        }
    }

    private static void assertGrant(String method, String path, String upgrade,
                                    Scope scope, Long runId) {
        var grant = ToolEndpointSecurityPolicy.match(method, path, upgrade).orElseThrow();
        assertTrue(grant.scope() == scope && grant.runId().equals(runId));
    }
}
