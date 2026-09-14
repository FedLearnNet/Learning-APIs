package bio.cosy.feddb.local.api.cohort.member;

import bio.cosy.feddb.local.api.cohort.CohortAO;
import bio.cosy.feddb.local.api.cohort.CohortEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.ForbiddenException;

import java.util.Optional;
import java.util.Set;

@ApplicationScoped
public class CohortMemberAuthBO {

    @Inject
    CohortAO cohortAO;

    public Optional<CohortMemberTypes> getMemberType(Long cohortId, String keycloakId) {
        return cohortAO.findByIdOptional(cohortId)
                .flatMap(cohort -> getMemberType(cohort, keycloakId));
    }

    public Optional<CohortMemberTypes> getMemberType(CohortEntity cohort, String keycloakId) {
        if (cohort.getKeycloakId().equals(keycloakId)) {
            return Optional.of(CohortMemberTypes.MAINTAINER);
        }
        Set<CohortMemberEntity> members = cohort.getMembers();
        return members.stream()
                .filter(member -> member
                        .getKeycloakId().equals(keycloakId)).
                findFirst().map(CohortMemberEntity::getType);
    }

    public boolean isMember(Long cohortId, String keycloakId) {
        return getMemberType(cohortId, keycloakId).isPresent();
    }

    public boolean isMember(CohortEntity cohort, String keycloakId) {
        return getMemberType(cohort, keycloakId).isPresent();
    }

    public void checkForEdit(Long cohortId, String keycloakId) {
        checkForEdit(getMemberType(cohortId, keycloakId));
    }

    public void checkForMember(Long cohortId, String keycloakId) {
        if (!isMember(cohortId, keycloakId)) {
            throw new ForbiddenException("You are not allowed to access this cohort");
        }
    }

    public void checkForEditPatients(Long cohortId, String keycloakId) {
        checkForEditPatients(getMemberType(cohortId, keycloakId));
    }

    public boolean canEditPatients(Long cohortId, String keycloakId) {
        return getMemberType(cohortId, keycloakId)
                .map(CohortMemberTypes::canEditPatients)
                .orElse(false);
    }

    public void checkForDelete(Long cohortId, String keycloakId) {
        checkForDelete(getMemberType(cohortId, keycloakId));
    }

    public void checkForEdit(CohortEntity cohort, String keycloakId) {
        checkForEdit(getMemberType(cohort, keycloakId));
    }

    public void checkForEditPatients(CohortEntity cohort, String keycloakId) {
        checkForEditPatients(getMemberType(cohort, keycloakId));

    }

    public void checkForDelete(CohortEntity cohort, String keycloakId) {
        checkForDelete(getMemberType(cohort, keycloakId));
    }

    public void checkForEdit(Optional<CohortMemberTypes> member) {
        if (member.isEmpty() || !member.get().canEdit()) {
            throw new ForbiddenException("You are not allowed to add operations on this cohort");
        }
    }

    public void checkForEdit(CohortMemberTypes member) {
        if (!member.canEdit()) {
            throw new ForbiddenException("You are not allowed to add operations on this cohort");
        }
    }

    public void checkForEditPatients(Optional<CohortMemberTypes> member) {
        if (member.isEmpty() || !member.get().canEditPatients()) {
            throw new ForbiddenException("You are not allowed to edit patients on this cohort");
        }
    }

    public void checkForEditPatients(CohortMemberTypes member) {
        if (!member.canEditPatients()) {
            throw new ForbiddenException("You are not allowed to edit patients on this cohort");
        }
    }

    public void checkForDelete(Optional<CohortMemberTypes> member) {
        if (member.isEmpty() || !member.get().canDelete()) {
            throw new ForbiddenException("You are not allowed to delete operations on this cohort");
        }
    }

    public void checkForDelete(CohortMemberTypes member) {
        if (!member.canDelete()) {
            throw new ForbiddenException("You are not allowed to delete operations on this cohort");
        }
    }

}
