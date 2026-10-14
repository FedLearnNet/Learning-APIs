package de.unihamburg.daibetes.api.project.experiment.federated.step;

import bio.cosy.feddb.core.api.workflow.base.step.BaseWorkflowStepAO;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.util.Date;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class ProjectFederatedExperimentStepAO extends BaseWorkflowStepAO<ProjectFederatedExperimentStepEntity> {

    /**
     * Steps of the experiment whose relay run was created but not stopped yet.
     */
    public List<ProjectFederatedExperimentStepEntity> findWithOpenRelayRun(final Long experimentId) {
        return list("experiment.id = ?1 and channel is not null and (relayStopped is null or relayStopped = false)", experimentId);
    }

    /**
     * Stores the relay run created for the step: its channel, the relay server's key and which relay client id
     * was assigned to which clinic.
     */
    @Transactional
    public boolean setRelayRunTransactional(final Long id, final String channel, final String relayKey, final Map<String, String> relayClientIds) {
        int updated = update(
                """
                        updatedAt = ?1,
                        channel = ?2,
                        relayKey = ?3,
                        relayClientIds = ?4,
                        relayStopped = false
                        where id = ?5
                        """,
                new Date(),
                channel,
                relayKey,
                relayClientIds,
                id
        );
        return updated == 1;
    }

    /**
     * Marks the relay run of the step as stopped on the relay server.
     */
    @Transactional
    public boolean setRelayStoppedTransactional(final Long id) {
        int updated = update(
                """
                        updatedAt = ?1,
                        relayStopped = true
                        where id = ?2
                        """,
                new Date(),
                id
        );
        return updated == 1;
    }

}
