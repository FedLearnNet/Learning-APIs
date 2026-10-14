package bio.cosy.feddb.core.security;

import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.vertx.http.runtime.security.HttpSecurityPolicy;
import io.smallrye.mutiny.Uni;
import io.vertx.ext.web.RoutingContext;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Shared enforcement for callbacks made by Tool containers.
 * <p>
 * Implements the {@link HttpSecurityPolicy} interface, so if a module uses this
 * Quarkus applies this globally to every HTTP request and WebSocket upgrade
 * Each module (global-learning-api, local-learning-api) has one subclass, {@code ToolEndpointSecurityPolicy},
 * that lists its tool callback paths in {@link #rules()}.
 * <p>
 * IMPORTANT: if an endpoint has no rule here, the policy treats it like any other endpoint and
 * permits every non-key identity, including Keycloak users. That is why endpoints marked with
 * {@link ToolAuthenticated} must also set a rule in the relevant subclass of this class.
 * They should also carry {@code @RolesAllowed(ToolApiKeyAuthenticationMechanism.ROLE)}
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

    /**
     * Returns the grant a request needs, or empty if it is not a tool endpoint.
     *
     * @param upgrade the request's {@code Upgrade} header, used to tell WebSocket rules apart
     */
    protected static Optional<RequestGrant> match(List<Rule> rules, String method, String path, String upgrade) {
        return rules.stream()
                .map(rule -> rule.match(method, path, upgrade))
                .flatMap(Optional::stream)
                .findFirst();
    }

    protected static Rule post(Scope scope, String path) {
        return new Rule("POST", Pattern.compile("^" + path + "/?$", Pattern.CASE_INSENSITIVE), false, scope);
    }

    protected static Rule put(Scope scope, String path) {
        return new Rule("PUT", Pattern.compile("^" + path + "/?$", Pattern.CASE_INSENSITIVE), false, scope);
    }

    protected static Rule get(Scope scope, String path) {
        return new Rule("GET", Pattern.compile("^" + path + "/?$", Pattern.CASE_INSENSITIVE), false, scope);
    }

    protected static Rule websocket(Scope scope, String path) {
        return new Rule("GET", Pattern.compile("^" + path + "/?$", Pattern.CASE_INSENSITIVE), true, scope);
    }

    /** The scope and run id an API key must have to call the matched endpoint. */
    public record RequestGrant(Scope scope, Long runId) {
    }

    /*
     * We define the Rule record here to set the match method. Then in the subclass we
     * just need to define the rule and match can be used automatically
     * Logic is:
     * - check whether the method matches (exception for websocket)
     * - check whether the path matches (regex)
     * - if both match, extract the runId from the path and return a RequestGrant
     * That can then be checked against the API key's scope and runId in matchesToolIdentity
     */
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