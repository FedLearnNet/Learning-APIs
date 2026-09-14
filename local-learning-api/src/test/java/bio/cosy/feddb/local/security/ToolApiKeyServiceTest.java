package bio.cosy.feddb.local.security;

import bio.cosy.feddb.core.security.ToolApiKeyService;
import bio.cosy.feddb.core.security.Scope;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolApiKeyServiceTest {

    @Test
    void keyIsUniqueAndBoundToScopeAndRun() {
        ToolApiKeyService service = new ToolApiKeyService();
        String first = service.issue(Scope.LEARNING_RUN, 42L);
        String second = service.issue(Scope.LEARNING_RUN, 42L);

        assertNotEquals(first, second);
        assertFalse(service.isAuthorized(first, Scope.LEARNING_RUN, 42L));
        assertTrue(service.isAuthorized(second, Scope.LEARNING_RUN, 42L));
        assertFalse(service.isAuthorized(second, Scope.LEARNING_RUN, 43L));
        assertFalse(service.isAuthorized(second, Scope.MODEL_PREDICTION_RUN, 42L));
        assertFalse(service.isAuthorized("wrong-key", Scope.LEARNING_RUN, 42L));
        var identity = service.resolve(second).orElseThrow();
        assertTrue(identity.scope() == Scope.LEARNING_RUN
                && identity.runId().equals(42L));

        service.revoke(second);
        assertFalse(service.isAuthorized(second, Scope.LEARNING_RUN, 42L));
    }

    @Test
    void predictionKeyCannotAuthenticateAsWorkflowWithTheSameNumericId() {
        ToolApiKeyService service = new ToolApiKeyService();
        String apiKey = service.issue(Scope.MODEL_PREDICTION_RUN, 42L);

        assertTrue(service.isAuthorized(apiKey, Scope.MODEL_PREDICTION_RUN, 42L));
        assertFalse(service.isAuthorized(apiKey, Scope.MODEL_WORKFLOW_RUN, 42L));
    }

    @Test
    void keyCanBeRevokedByItsRunIdentity() {
        ToolApiKeyService service = new ToolApiKeyService();
        String apiKey = service.issue(Scope.CONNECTOR_RUN, 3L);

        service.revoke(Scope.CONNECTOR_RUN, 3L);

        assertFalse(service.isAuthorized(apiKey, Scope.CONNECTOR_RUN, 3L));
    }
}
