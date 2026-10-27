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
        assertGrant("POST", "/model/run/prediction/42/upload/output", null,
                Scope.MODEL_PREDICTION_RUN, 42L);
        assertGrant("POST", "/model/run/workflow/42/upload/output", null,
                Scope.MODEL_WORKFLOW_RUN, 42L);
        assertGrant("GET", "/model/run/workflow/42/app", "websocket",
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
        assertFalse(ToolEndpointSecurityPolicy.match("GET", "/testembed/42/app", "websocket").isPresent());
        assertFalse(ToolEndpointSecurityPolicy.match("GET", "/query/clients", "websocket").isPresent());

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

    private static void assertGrant(String method, String path, String upgrade,
                                    Scope scope, Long runId) {
        var grant = ToolEndpointSecurityPolicy.match(method, path, upgrade).orElseThrow();
        assertTrue(grant.scope() == scope && grant.runId().equals(runId));
    }
}
