package de.unihamburg.daibetes.security;

import bio.cosy.feddb.core.security.BaseToolEndpointSecurityPolicy;
import bio.cosy.feddb.core.security.Scope;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Optional;

/**
 * Restricts tool API-key identities to their run-scoped callback endpoints.
 */
@ApplicationScoped
public class ToolEndpointSecurityPolicy extends BaseToolEndpointSecurityPolicy {

    private static final List<Rule> RULES = List.of(
            post(Scope.MODEL_PREDICTION_RUN, "/model/run/prediction/([^/]+)/upload/output"),
            websocket(Scope.MODEL_PREDICTION_RUN, "/model/run/prediction/([^/]+)/app"),
            post(Scope.MODEL_WORKFLOW_RUN, "/model/run/workflow/([^/]+)/upload/output"),
            websocket(Scope.MODEL_WORKFLOW_RUN, "/model/run/workflow/([^/]+)/app"),
            websocket(Scope.LOCAL_EXPERIMENT_RUN, "/project/experiment/local/([^/]+)/app")
    );

    @Override
    protected List<Rule> rules() {
        return RULES;
    }

    static Optional<RequestGrant> match(String method, String path, String upgrade) {
        return BaseToolEndpointSecurityPolicy.match(RULES, method, path, upgrade);
    }
}
