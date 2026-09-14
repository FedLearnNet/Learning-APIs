package bio.cosy.feddb.local.api.cohort.patient.traceability.crud;

import io.quarkus.arc.Arc;
import io.quarkus.logging.Log;
import org.hibernate.envers.RevisionListener;

import static bio.cosy.feddb.local.api.auth.UserIdentity.DEFAULT_KEYCLOAK_ID;

public class UserRevisionListener implements RevisionListener {

    @Override
    public void newRevision(Object revisionEntity) {
        if (revisionEntity instanceof CustomRevisionEntity customRevision) {
            AuditContext context = getAuditContext();
            AuditContext.AuditMetadata threadLocalContext = AuditContext.getThreadLocalContext();

            String keycloakId = context != null ? context.getKeycloakId()
                    : threadLocalContext != null ? threadLocalContext.keycloakId() : null;
            Long connectorId = context != null ? context.getConnectorId()
                    : threadLocalContext != null ? threadLocalContext.connectorId() : null;
            Long runId = context != null ? context.getRunId()
                    : threadLocalContext != null ? threadLocalContext.runId() : null;

            if (keycloakId == null) {
                Log.warn("Keycloak ID is null, cannot set keycloakId for revision entity.");
                keycloakId = DEFAULT_KEYCLOAK_ID;
            }
            customRevision.setKeycloakId(keycloakId);
            customRevision.setConnectorId(connectorId);
            customRevision.setRunId(runId);
        } else {
            Log.error("Revision entity is not an instance of CustomRevisionEntity: " + revisionEntity.getClass().getName());
        }
    }

    private AuditContext getAuditContext() {
        if (!Arc.container().requestContext().isActive()) {
            Log.debug("Request context is not active while creating revision metadata.");
            return null;
        }
        var contextInstance = Arc.container().instance(AuditContext.class);
        if (!contextInstance.isAvailable()) {
            return null;
        }
        return contextInstance.get();
    }
}
