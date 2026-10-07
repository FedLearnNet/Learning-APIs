package de.unihamburg.daibetes.api.project.experiment.federated.step;

import bio.cosy.feddb.core.api.socket.FederatedLearningRelayInfoDTO;

import java.util.Map;

/**
 * Result of {@link ProjectFederatedExperimentStepBO#handleRelaySetup(de.unihamburg.daibetes.api.project.experiment.federated.ProjectFederatedExperimentEntity)}.
 *
 * @param clientRelayData  relay credentials keyed by clinic (uniqueRandomClinicId), broadcast to
 *                         clinics as today.
 * @param platformRelayInfo the coordinator-only relay slot, present only when no clinic is marked
 *                          coordinator (i.e. platformIsCoordinator is active for this experiment).
 */
public record FederatedRelaySetupResult(
        Map<String, FederatedLearningRelayInfoDTO> clientRelayData,
        FederatedLearningRelayInfoDTO platformRelayInfo
) {
}
