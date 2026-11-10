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
            post(Scope.EXECUTION_ATTEMPT, "/execution/APP/([^/]+)/upload/(?:output|model)"),
            websocket(Scope.EXECUTION_ATTEMPT, "/execution/APP/([^/]+)/app"),
            post(Scope.MODEL_PREDICTION_RUN, "/execution/prediction/([^/]+)/upload/output"),
            websocket(Scope.MODEL_PREDICTION_RUN, "/execution/prediction/([^/]+)/app"),
            post(Scope.MODEL_WORKFLOW_RUN, "/execution/workflow/([^/]+)/upload/output"),
            // the model a training on the platform trained
            post(Scope.MODEL_WORKFLOW_RUN, "/execution/workflow/([^/]+)/upload/model"),
            websocket(Scope.MODEL_WORKFLOW_RUN, "/execution/workflow/([^/]+)/app"),
            // A test engine container talks like a developer's local engine, which logs in as a user
            websocketAlsoForUsers(Scope.TEST_EMBED_APP, "/testembed/([^/]+)/app"),
            // callbacks of the build pipeline container
            get(Scope.PIPELINE_RUN, "/pipeline/([^/]+)/info"),
            get(Scope.PIPELINE_RUN, "/pipeline/([^/]+)/zip"),
            put(Scope.PIPELINE_RUN, "/pipeline/([^/]+)/update"),
            put(Scope.PIPELINE_RUN, "/pipeline/([^/]+)/summary")
    );

    @Override
    protected List<Rule> rules() {
        return RULES;
    }

    static Optional<RequestGrant> match(String method, String path, String upgrade) {
        return BaseToolEndpointSecurityPolicy.match(RULES, method, path, upgrade);
    }
}
