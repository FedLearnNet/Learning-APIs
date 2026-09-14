package bio.cosy.feddb.local.api.cohort.patient.traceability.crud;

import bio.cosy.feddb.local.api.auth.UserIdentity;
import io.quarkus.arc.Arc;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import lombok.*;


@RequestScoped
@Data
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class AuditContext {
    private static final ThreadLocal<AuditMetadata> THREAD_LOCAL_CONTEXT = new ThreadLocal<>();

    @Inject
    UserIdentity userIdentity;

    private String keycloakId;
    private Long connectorId;
    private Long runId;

    // For HTTP requests
    public AuditContext(String keycloakId) {
        this.keycloakId = keycloakId;
        this.connectorId = null;
        this.runId = null;
    }

    public static void setThreadLocalContext(String keycloakId, Long connectorId, Long runId) {
        THREAD_LOCAL_CONTEXT.set(new AuditMetadata(keycloakId, connectorId, runId));
    }

    public static AuditMetadata getThreadLocalContext() {
        return THREAD_LOCAL_CONTEXT.get();
    }

    public static void clearThreadLocalContext() {
        THREAD_LOCAL_CONTEXT.remove();
    }

    public String getKeycloakId() {
        if (keycloakId != null) {
            return keycloakId;
        }
        AuditMetadata threadLocalContext = getThreadLocalContext();
        if (threadLocalContext != null && threadLocalContext.keycloakId() != null) {
            return threadLocalContext.keycloakId();
        }
        if (Arc.container().requestContext().isActive() && userIdentity != null && userIdentity.isLoggedIn()) {
            return userIdentity.getKeycloakId();
        }
        return null;
    }

    public record AuditMetadata(String keycloakId, Long connectorId, Long runId) {
    }
}
