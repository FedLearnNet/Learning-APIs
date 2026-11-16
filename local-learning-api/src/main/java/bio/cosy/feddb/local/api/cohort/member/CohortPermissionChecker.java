package bio.cosy.feddb.local.api.cohort.member;

import io.quarkus.security.PermissionChecker;
import io.quarkus.security.identity.SecurityIdentity;
import io.smallrye.common.annotation.Blocking;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;


@ApplicationScoped
public class CohortPermissionChecker {
    public static final String READ = "cohort-read";
    public static final String EDIT = "cohort-edit";
    public static final String EDIT_PATIENTS = "cohort-edit-patients";
    public static final String DELETE = "cohort-delete";

    @Inject
    CohortMemberAuthBO cohortMemberAuthBO;

    @Blocking
    @ActivateRequestContext
    @PermissionChecker(READ)
    boolean canRead(SecurityIdentity identity, Long cohortId) {
        return cohortMemberAuthBO.isMember(cohortId, keycloakId(identity));
    }

    @Blocking
    @ActivateRequestContext
    @PermissionChecker(EDIT)
    boolean canEdit(SecurityIdentity identity, Long cohortId) {
        return cohortMemberAuthBO.getMemberType(cohortId, keycloakId(identity))
                .map(CohortMemberTypes::canEdit)
                .orElse(false);
    }

    @Blocking
    @ActivateRequestContext
    @PermissionChecker(EDIT_PATIENTS)
    boolean canEditPatients(SecurityIdentity identity, Long cohortId) {
        return cohortMemberAuthBO.canEditPatients(cohortId, keycloakId(identity));
    }

    @Blocking
    @ActivateRequestContext
    @PermissionChecker(DELETE)
    boolean canDelete(SecurityIdentity identity, Long cohortId) {
        return cohortMemberAuthBO.canDelete(cohortId, keycloakId(identity));
    }

    // same id as UserIdentity.getKeycloakId()
    private static String keycloakId(SecurityIdentity identity) {
        return identity.getPrincipal().getName();
    }
}
