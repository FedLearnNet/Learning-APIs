package bio.cosy.feddb.local.security;

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
            post(Scope.CONNECTOR_RUN, "/connectors/run/execution/([^/]+)/upload/output"),
            websocket(Scope.CONNECTOR_RUN, "/connectors/run/execution/([^/]+)/app"),
            post(Scope.LEARNING_RUN, "/learning/run/([^/]+)/upload/(?:output|model)"),
            websocket(Scope.LEARNING_RUN, "/learning/run/([^/]+)/app"),
            websocket(Scope.PATIENT_TOOL_RUN, "/patient/tool/run/([^/]+)/app")
    );

    @Override
    protected List<Rule> rules() {
        return RULES;
    }

    static Optional<RequestGrant> match(String method, String path, String upgrade) {
        return BaseToolEndpointSecurityPolicy.match(RULES, method, path, upgrade);
    }
}
