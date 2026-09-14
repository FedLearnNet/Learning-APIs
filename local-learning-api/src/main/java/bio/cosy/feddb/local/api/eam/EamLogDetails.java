package bio.cosy.feddb.local.api.eam;

import io.quarkus.oidc.client.OidcClientException;
import io.vertx.core.http.UpgradeRejectedException;
import io.vertx.core.json.JsonObject;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.StringJoiner;

final class EamLogDetails {
    private static final Set<String> OAUTH_ERRORS = Set.of(
            "invalid_request", "invalid_client", "invalid_grant", "unauthorized_client",
            "unsupported_grant_type", "invalid_scope", "access_denied", "server_error",
            "temporarily_unavailable");

    private EamLogDetails() {
    }

    // Exception messages and response bodies can contain credentials or tokens. Only log
    // exception types, HTTP status codes and recognized OAuth error codes.
    static String failure(Throwable failure) {
        StringJoiner details = new StringJoiner("; ");
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Throwable cause = failure; cause != null && seen.add(cause); cause = cause.getCause()) {
            details.add("type=" + cause.getClass().getSimpleName());
            if (cause instanceof UpgradeRejectedException rejected) {
                details.add("httpStatus=" + rejected.getStatus());
                if (rejected.getStatus() == 401 || rejected.getStatus() == 403) {
                    details.add("authentication/authorization rejected; check token validity and global access permissions");
                }
            }
            if (cause instanceof OidcClientException && cause.getMessage() != null) {
                try {
                    String error = new JsonObject(cause.getMessage()).getString("error");
                    if (error != null && OAUTH_ERRORS.contains(error)) {
                        details.add("oauthError=" + error);
                    }
                } catch (RuntimeException ignored) {
                    // Non-JSON provider errors are intentionally omitted.
                }
            }
        }
        return details.toString();
    }
}
