package de.unihamburg.daibetes.api.project.experiment.federated.step;

import bio.cosy.feddb.core.api.run.RunStatusTypes;
import bio.cosy.feddb.core.api.socket.FederatedLearningRelayInfoDTO;
import bio.cosy.feddb.core.api.workflow.base.step.BaseWorkflowStepAO;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.util.Date;

@ApplicationScoped
public class ProjectFederatedExperimentStepAO extends BaseWorkflowStepAO<ProjectFederatedExperimentStepEntity> {

    /**
     * Persists the platform's own aggregator container id and relay credentials for this step.
     * Deliberately does not touch stepStatus - that field tracks the clinic-aggregated round
     * status (see ProjectFederatedExperimentBO), a separate concern from this single container's
     * own lifecycle. Guarded the same way every sibling BaseWorkflowStepAO mutator is, so a
     * delayed/retried write can't resurrect containerId/platformRelayInfo on a step that already
     * reached a terminal status (e.g. a stop() that already landed).
     */
    @Transactional
    public boolean setPlatformAggregatorContainerTransactional(Long stepId, String containerId, FederatedLearningRelayInfoDTO relayInfo) {
        int updated = update(
                """
                        containerId = ?1,
                        platformRelayInfo = ?2,
                        updatedAt = ?3
                        where id = ?4
                          and (stepStatus is null or stepStatus not in ?5)
                        """,
                containerId,
                relayInfo,
                new Date(),
                stepId,
                RunStatusTypes.terminalStates()
        );
        if (updated > 1) {
            throw new IllegalStateException("Multiple steps updated for stepId " + stepId);
        }
        return updated == 1;
    }
}
