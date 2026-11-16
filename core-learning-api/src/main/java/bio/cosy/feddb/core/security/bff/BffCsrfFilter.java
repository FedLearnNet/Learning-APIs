package bio.cosy.feddb.core.security.bff;

import jakarta.ws.rs.HttpMethod;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.PreMatching;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;

import java.util.Set;

/**
 * CSRF protection for cookie-authenticated (browser session) requests: state-changing requests must
 * carry the custom X-Requested-With header. Browsers only send custom headers cross-origin after a
 * CORS preflight, which a foreign origin does not pass (OWASP "custom request header" defense).
 * Bearer token and tool API-key callers do not use the session cookie and are not affected.
 */
@Provider
@PreMatching
public class BffCsrfFilter implements ContainerRequestFilter {

    static final String SESSION_COOKIE_PREFIX = "q_session";
    static final String CSRF_HEADER = "X-Requested-With";
    private static final Set<String> SAFE_METHODS = Set.of(HttpMethod.GET, HttpMethod.HEAD, HttpMethod.OPTIONS);

    @Override
    public void filter(ContainerRequestContext request) {
        if (SAFE_METHODS.contains(request.getMethod())
                || request.getHeaderString(HttpHeaders.AUTHORIZATION) != null
                || !hasSessionCookie(request)
                || request.getHeaderString(CSRF_HEADER) != null) {
            return;
        }
        request.abortWith(Response.status(Response.Status.FORBIDDEN)
                .entity("Missing " + CSRF_HEADER + " header")
                .build());
    }

    private static boolean hasSessionCookie(ContainerRequestContext request) {
        return request.getCookies().keySet().stream().anyMatch(name -> name.startsWith(SESSION_COOKIE_PREFIX));
    }
}
