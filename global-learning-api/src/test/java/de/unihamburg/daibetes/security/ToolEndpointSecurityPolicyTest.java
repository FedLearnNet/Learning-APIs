package de.unihamburg.daibetes.security;

import bio.cosy.feddb.core.security.Scope;
import bio.cosy.feddb.core.security.ToolAuthenticated;
import de.unihamburg.daibetes.execution.attempt.ExecutionAttemptWebsocketService;
import de.unihamburg.daibetes.api.development.testembed.TestEmbedService;
import io.quarkus.security.Authenticated;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolEndpointSecurityPolicyTest {

    @Test
    void distinguishesToolAuthenticationFromUserAuthentication() {
        assertTrue(ToolAuthenticated.class.isAnnotationPresent(Authenticated.class));
        assertTrue(ExecutionAttemptWebsocketService.class.isAnnotationPresent(ToolAuthenticated.class));
        assertTrue(TestEmbedService.class.isAnnotationPresent(Authenticated.class));
        assertFalse(TestEmbedService.class.isAnnotationPresent(ToolAuthenticated.class));
    }

    @Test
    void allowsOnlyToolCallbacks() {
        assertGrant("POST", "/execution/APP/42/upload/output", null, Scope.EXECUTION_ATTEMPT, 42L);
        assertGrant("POST", "/execution/APP/42/upload/model", null, Scope.EXECUTION_ATTEMPT, 42L);
        assertGrant("GET", "/execution/APP/42/app", "websocket", Scope.EXECUTION_ATTEMPT, 42L);
        assertGrant("POST", "/execution/prediction/42/upload/output", null,
                Scope.MODEL_PREDICTION_RUN, 42L);
        assertGrant("POST", "/execution/workflow/42/upload/output", null,
                Scope.MODEL_WORKFLOW_RUN, 42L);
        assertGrant("GET", "/execution/workflow/42/app", "websocket",
                Scope.MODEL_WORKFLOW_RUN, 42L);
        assertGrant("GET", "/pipeline/42/info", null, Scope.PIPELINE_RUN, 42L);
        assertGrant("GET", "/pipeline/42/zip", null, Scope.PIPELINE_RUN, 42L);
        assertGrant("PUT", "/pipeline/42/update", null, Scope.PIPELINE_RUN, 42L);
        assertGrant("PUT", "/pipeline/42/summary", null, Scope.PIPELINE_RUN, 42L);

        assertFalse(ToolEndpointSecurityPolicy.match("POST", "/pipeline/start/42", null).isPresent());
        assertFalse(ToolEndpointSecurityPolicy.match("GET", "/pipeline/42", null).isPresent());
        assertFalse(ToolEndpointSecurityPolicy.match("GET", "/pipeline/42/stream", null).isPresent());
        assertFalse(ToolEndpointSecurityPolicy.match("PUT", "/pipeline/42/stop", null).isPresent());
        assertFalse(ToolEndpointSecurityPolicy.match("GET", "/model/run/workflow/42/download", null).isPresent());
        assertFalse(ToolEndpointSecurityPolicy.match("GET", "/project", null).isPresent());
        assertFalse(ToolEndpointSecurityPolicy.match("GET", "/testembed/42/client", "websocket").isPresent());
        assertFalse(ToolEndpointSecurityPolicy.match("GET", "/query/clients", "websocket").isPresent());

    }

    @Test
    void testEngineEndpointsAlsoAllowUsers() {
        assertGrant("GET", "/testembed/42/app", "websocket", Scope.TEST_EMBED_APP, 42L);
        assertTrue(ToolEndpointSecurityPolicy.match("GET", "/testembed/42/app", "websocket").orElseThrow().usersAllowed());
        assertFalse(ToolEndpointSecurityPolicy.match("GET", "/execution/workflow/42/app", "websocket").orElseThrow().usersAllowed());
        assertFalse(ToolEndpointSecurityPolicy.match("GET", "/testembed/42/upload/output/7", null).isPresent());
    }

    @Test
    void matchesOnlyExactPipelineCallbackPaths() {
        assertGrant("PUT", "/pipeline/42/update/", null, Scope.PIPELINE_RUN, 42L);

        assertFalse(ToolEndpointSecurityPolicy.match("PUT", "/pipeline/abc/update", null).isPresent());
        assertFalse(ToolEndpointSecurityPolicy.match("GET", "/pipeline/42/update", null).isPresent());
        assertFalse(ToolEndpointSecurityPolicy.match("POST", "/pipeline/42/update", null).isPresent());
        assertFalse(ToolEndpointSecurityPolicy.match("PUT", "/pipeline/42/update/extra", null).isPresent());
        assertFalse(ToolEndpointSecurityPolicy.match("PUT", "/x/pipeline/42/update", null).isPresent());
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
