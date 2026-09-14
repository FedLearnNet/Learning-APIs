package de.unihamburg.daibetes.security;

import bio.cosy.feddb.core.security.ToolAuthenticated;
import de.unihamburg.daibetes.api.analysis.run.DataAnalysisRunWebsocketService;
import de.unihamburg.daibetes.api.testembed.TestEmbedService;
import io.quarkus.security.Authenticated;
import org.junit.jupiter.api.Test;

import bio.cosy.feddb.core.security.Scope;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolEndpointSecurityPolicyTest {

    @Test
    void distinguishesToolAuthenticationFromUserAuthentication() {
        assertTrue(ToolAuthenticated.class.isAnnotationPresent(Authenticated.class));
        assertTrue(DataAnalysisRunWebsocketService.class.isAnnotationPresent(ToolAuthenticated.class));
        assertTrue(TestEmbedService.class.isAnnotationPresent(Authenticated.class));
        assertFalse(TestEmbedService.class.isAnnotationPresent(ToolAuthenticated.class));
    }

    @Test
    void allowsOnlyToolCallbacks() {
        assertGrant("POST", "/model/run/prediction/42/upload/output", null,
                Scope.MODEL_PREDICTION_RUN, 42L);
        assertGrant("POST", "/model/run/workflow/42/upload/output", null,
                Scope.MODEL_WORKFLOW_RUN, 42L);
        assertGrant("GET", "/project/experiment/local/42/app", "websocket",
                Scope.LOCAL_EXPERIMENT_RUN, 42L);
        assertFalse(ToolEndpointSecurityPolicy.match("POST", "/pipeline/start/42", null).isPresent());
        assertFalse(ToolEndpointSecurityPolicy.match("PUT", "/pipeline/42/update", null).isPresent());
        assertFalse(ToolEndpointSecurityPolicy.match("GET", "/pipeline/42/zip", null).isPresent());
        assertFalse(ToolEndpointSecurityPolicy.match("GET", "/model/run/workflow/42/download", null).isPresent());
        assertFalse(ToolEndpointSecurityPolicy.match("GET", "/project", null).isPresent());
        assertFalse(ToolEndpointSecurityPolicy.match("GET", "/testembed/42/app", "websocket").isPresent());
        assertFalse(ToolEndpointSecurityPolicy.match("GET", "/query/clients", "websocket").isPresent());

    }

    private static void assertGrant(String method, String path, String upgrade,
                                    Scope scope, Long runId) {
        var grant = ToolEndpointSecurityPolicy.match(method, path, upgrade).orElseThrow();
        assertTrue(grant.scope() == scope && grant.runId().equals(runId));
    }
}
