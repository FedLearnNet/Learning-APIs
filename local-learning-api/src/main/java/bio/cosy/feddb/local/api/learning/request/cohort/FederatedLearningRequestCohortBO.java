package bio.cosy.feddb.local.api.learning.request.cohort;

import bio.cosy.feddb.local.api.cohort.CohortAO;
import bio.cosy.feddb.local.api.cohort.CohortEntity;
import bio.cosy.feddb.local.api.cohort.member.CohortMemberAuthBO;
import bio.cosy.feddb.local.api.cohort.member.CohortMemberTypes;
import bio.cosy.feddb.local.api.cohort.permission.PermissionBO;
import bio.cosy.feddb.local.api.learning.request.FederatedLearningRequestEntity;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@ApplicationScoped
public class FederatedLearningRequestCohortBO {

    @Inject
    FederatedLearningRequestCohortAO ao;

    @Inject
    CohortAO cohortAO;

    @Inject
    PermissionBO permissionBO;

    @Inject
    CohortMemberAuthBO cohortMemberAuthBO;

    /**
     * Creates one pending-or-auto-approved row per involved cohort.
     *
     * @return cohort ids that still need a manual decision
     */
    public Set<Long> initForRequest(FederatedLearningRequestEntity request,
                                    Set<Long> cohortIds,
                                    String platformUserId,
                                    int certificationLevel,
                                    boolean needsInternetAccess) {
        for (Long cohortId : cohortIds) {
            CohortEntity cohort = cohortAO.findByIdOptional(cohortId)
                    .orElseThrow(() -> new NotFoundException("Cohort not found with id: " + cohortId));

            FederatedLearningRequestCohortEntity decision = new FederatedLearningRequestCohortEntity();
            decision.setRequest(request);
            decision.setCohort(cohort);
            boolean autoApproved = permissionBO.hasAutoTrainingAccessForCohort(
                    platformUserId, cohortId, certificationLevel, needsInternetAccess);
            decision.setStatus(autoApproved
                    ? FederatedLearningRequestCohortStatus.APPROVED
                    : FederatedLearningRequestCohortStatus.PENDING);
            ao.persist(decision);
            Log.infof("Training request %s cohort %s initialised as %s",
                    request.getId(), cohortId, decision.getStatus());
        }
        return getPendingCohortIds(request.getId());
    }

    public List<FederatedLearningRequestCohortDTO> findByRequestForUser(Long requestId,
                                                                        String keycloakId) {
        return ao.findByRequest(requestId).stream()
                .map(entity -> {
                    FederatedLearningRequestCohortDTO dto =
                            new FederatedLearningRequestCohortDTO();
                    dto.setCohortId(entity.getCohort().getId());
                    dto.setStatus(entity.getStatus());
                    dto.setDecidableByCurrentUser(
                            entity.getStatus() == FederatedLearningRequestCohortStatus.PENDING
                                    && canDecide(entity.getCohort().getId(), keycloakId));
                    return dto;
                })
                .toList();
    }

    public Set<Long> getPendingCohortIds(Long requestId) {
        return ao.findByRequest(requestId).stream()
                .filter(d -> d.getStatus() == FederatedLearningRequestCohortStatus.PENDING)
                .map(d -> d.getCohort().getId())
                .collect(Collectors.toSet());
    }

    public Set<Long> getApprovedCohortIds(Long requestId) {
        return ao.findByRequest(requestId).stream()
                .filter(d -> d.getStatus() == FederatedLearningRequestCohortStatus.APPROVED)
                .map(d -> d.getCohort().getId())
                .collect(Collectors.toSet());
    }

    public Set<Long> getCohortIds(Long requestId) {
        return ao.findCohortIdsByRequest(requestId);
    }

    public boolean hasPending(Long requestId) {
        return ao.hasPending(requestId);
    }

    public boolean hasApproved(Long requestId) {
        return ao.hasApproved(requestId);
    }

    public Set<Long> getDecidableCohortIds(Long requestId, String keycloakId) {
        return ao.findByRequest(requestId).stream()
                .filter(d -> d.getStatus() == FederatedLearningRequestCohortStatus.PENDING)
                .map(d -> d.getCohort().getId())
                .filter(cohortId -> canDecide(cohortId, keycloakId))
                .collect(Collectors.toSet());
    }

    public int decidePending(Long requestId,
                             Set<Long> cohortIds,
                             FederatedLearningRequestCohortStatus status) {
        return ao.decidePending(requestId, cohortIds, status);
    }

    private boolean canDecide(Long cohortId, String keycloakId) {
        return cohortMemberAuthBO.getMemberType(cohortId, keycloakId)
                .map(CohortMemberTypes::canEditPatients)
                .orElse(false);
    }
}
