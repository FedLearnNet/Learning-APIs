package bio.cosy.feddb.core.security;

import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.vertx.http.runtime.security.HttpSecurityPolicy;
import io.smallrye.mutiny.Uni;
import io.vertx.ext.web.RoutingContext;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Shared enforcement for callbacks made by computational tool containers.
 */
public abstract class BaseToolEndpointSecurityPolicy implements HttpSecurityPolicy {

    protected abstract List<Rule> rules();

    @Override
    public Uni<CheckResult> checkPermission(RoutingContext context,
                                            Uni<SecurityIdentity> identity,
                                            AuthorizationRequestContext requestContext) {
        return identity.onItem().transform(currentIdentity -> {
            Optional<RequestGrant> requestedGrant = match(rules(), context.request().method().name(),
                    context.normalizedPath(), context.request().getHeader("Upgrade"));
            if (requestedGrant.isPresent()) {
                return matchesToolIdentity(currentIdentity, requestedGrant.get())
                        ? CheckResult.PERMIT
                        : CheckResult.DENY;
            }

            // An API-key identity must never become a general-purpose API credential.
            return currentIdentity.hasRole(ToolApiKeyAuthenticationMechanism.ROLE)
                    ? CheckResult.DENY
                    : CheckResult.PERMIT;
        });
    }

    private boolean matchesToolIdentity(SecurityIdentity identity, RequestGrant requestedGrant) {
        Scope scope = identity.getAttribute(ToolApiKeyAuthenticationMechanism.SCOPE_ATTRIBUTE);
        Long runId = identity.getAttribute(ToolApiKeyAuthenticationMechanism.RUN_ID_ATTRIBUTE);
        return identity.hasRole(ToolApiKeyAuthenticationMechanism.ROLE)
                && scope == requestedGrant.scope()
                && requestedGrant.runId().equals(runId);
    }

    protected static Optional<RequestGrant> match(List<Rule> rules, String method, String path, String upgrade) {
        return rules.stream()
                .map(rule -> rule.match(method, path, upgrade))
                .flatMap(Optional::stream)
                .findFirst();
    }

    protected static Rule post(Scope scope, String path) {
        return new Rule("POST", Pattern.compile("^" + path + "/?$", Pattern.CASE_INSENSITIVE), false, scope);
    }

    protected static Rule websocket(Scope scope, String path) {
        return new Rule("GET", Pattern.compile("^" + path + "/?$", Pattern.CASE_INSENSITIVE), true, scope);
    }

    public record RequestGrant(Scope scope, Long runId) {
    }

    protected record Rule(String method, Pattern path, boolean websocket, Scope scope) {
        private Optional<RequestGrant> match(String requestMethod, String requestPath, String upgradeHeader) {
            if (!method.equalsIgnoreCase(requestMethod)
                    || (websocket && !"websocket".equalsIgnoreCase(upgradeHeader))) {
                return Optional.empty();
            }
            var matcher = path.matcher(requestPath);
            if (!matcher.matches()) {
                return Optional.empty();
            }
            try {
                return Optional.of(new RequestGrant(scope, Long.valueOf(matcher.group(1))));
            } catch (NumberFormatException e) {
                return Optional.empty();
            }
        }
    }
}
