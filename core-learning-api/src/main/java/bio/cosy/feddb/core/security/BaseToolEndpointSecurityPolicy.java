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
 * <p>
 * Subclasses are unnamed {@link HttpSecurityPolicy} beans, so Quarkus applies them globally to
 * every HTTP request and WebSocket upgrade, before any endpoint annotation is checked. Each module
 * (global-learning-api, local-learning-api) has one subclass, {@code ToolEndpointSecurityPolicy},
 * that lists its tool callback paths in {@link #rules()}.
 * <p>
 * Tool identities come from {@link ToolApiKeyAuthenticationMechanism}: a valid API key yields an
 * identity with role {@link ToolApiKeyAuthenticationMechanism#ROLE} plus the scope and run id the
 * key was issued for in {@link ToolApiKeyService}. The policy decides as follows:
 * <table>
 *   <tr><th>Request path</th><th>Identity</th><th>Result</th></tr>
 *   <tr><td>matches a rule</td><td>API key with the rule's scope and the run id from the path</td>
 *       <td>permit</td></tr>
 *   <tr><td>matches a rule</td><td>anything else (other key, Keycloak user, anonymous)</td>
 *       <td>deny</td></tr>
 *   <tr><td>matches no rule</td><td>API key</td>
 *       <td>deny, so a key never works as a general-purpose credential</td></tr>
 *   <tr><td>matches no rule</td><td>anything else</td>
 *       <td>permit, the endpoint's own annotations ({@code @Authenticated}, ...) decide</td></tr>
 * </table>
 * <p>
 * IMPORTANT: if a tool callback has no rule here, the policy treats it like any other endpoint and
 * permits every non-key identity, including Keycloak users. That is why endpoints marked with
 * {@link ToolAuthenticated} must also carry {@code @RolesAllowed(ToolApiKeyAuthenticationMechanism.ROLE)}:
 * the rule binds the key's scope and run id to the path, the role annotation keeps out everyone
 * who is not a tool. {@code ToolAuthenticatedEndpointsTest} in each module checks both.
 */
public abstract class BaseToolEndpointSecurityPolicy implements HttpSecurityPolicy {

    /**
     * The tool callback endpoints of this module. A request matching one of them is only permitted
     * for an API key with the rule's scope and the run id captured from the path.
     */
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
     * Returns the grant a request needs, or empty if it is not a tool callback.
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

    /** The scope and run id an API key must have to call the matched callback. */
    public record RequestGrant(Scope scope, Long runId) {
    }

    /**
     * One tool callback: HTTP method, anchored path pattern whose first group captures the run id
     * (must be numeric, otherwise the rule does not match), and the scope the key must have.
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