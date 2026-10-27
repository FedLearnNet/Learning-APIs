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
            post(Scope.TOOL_RUN, "/execution/tool/([^/]+)/upload/output"),
            websocket(Scope.TOOL_RUN, "/execution/tool/([^/]+)/app"),
            post(Scope.LEARNING_RUN, "/execution/learning/([^/]+)/upload/(?:output|model)"),
            websocket(Scope.LEARNING_RUN, "/execution/learning/([^/]+)/app")
    );

    @Override
    protected List<Rule> rules() {
        return RULES;
    }

    static Optional<RequestGrant> match(String method, String path, String upgrade) {
        return BaseToolEndpointSecurityPolicy.match(RULES, method, path, upgrade);
    }
}
