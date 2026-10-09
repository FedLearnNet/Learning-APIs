package de.unihamburg.daibetes.api.project.experiment.federated.step;

import bio.cosy.feddb.core.api.socket.FederatedLearningRelayInfoDTO;

import java.util.Collections;
import java.util.HashMap;
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
    public FederatedRelaySetupResult {
        // Defensive copy so a caller mutating its own map afterward can't reach back into this
        // already-returned result - clientRelayData is never null at either call site today.
        clientRelayData = Collections.unmodifiableMap(new HashMap<>(clientRelayData));
    }
}
