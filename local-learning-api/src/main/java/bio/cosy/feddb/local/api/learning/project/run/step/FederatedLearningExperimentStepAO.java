package bio.cosy.feddb.local.api.learning.project.run.step;

import bio.cosy.feddb.core.api.run.RunStatusTypes;
import bio.cosy.feddb.core.api.socket.FederatedLearningRelayInfoDTO;
import bio.cosy.feddb.core.api.workflow.base.step.BaseWorkflowStepAO;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.util.Date;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class FederatedLearningExperimentStepAO extends BaseWorkflowStepAO<FederatedLearningExperimentStepEntity> {

    public Optional<FederatedLearningExperimentStepEntity> findByProjectIdAndStep(Long projectId, Long step) {
        return find("experiment.project.id = ?1 and app.orderValue = ?2", projectId, step).firstResultOptional();
    }

    @Transactional
    public boolean setRelayInfoTransactional(Long id, FederatedLearningRelayInfoDTO relayInfo) {
        int updated = update(
                """
                        updatedAt = ?1,
                        relayInfo = ?2
                        where id = ?3
                        """,
                new Date(),
                relayInfo,
                id
        );
        return updated == 1;
    }

    /**
     * Steps that still wait for the signed relay certificate of their CSR.
     */
    public List<FederatedLearningExperimentStepEntity> findPendingRelayCerts() {
        return list("""
                        relayCsr is not null
                        and (relayCertSigned is null or relayCertSigned = false)
                        and (relayStopped is null or relayStopped = false)
                        and (stepStatus is null or stepStatus not in ?1)
                        """,
                RunStatusTypes.terminalStates());
    }

    /**
     * Steps of the experiment whose controller run was started but not stopped yet.
     */
    public List<FederatedLearningExperimentStepEntity> findWithOpenControllerRun(Long experimentId) {
        return list("experiment.id = ?1 and relayCsr is not null and (relayStopped is null or relayStopped = false)", experimentId);
    }

    @Transactional
    public boolean startRelayCertRequestTransactional(Long id, String csr) {
        int updated = update(
                """
                        updatedAt = ?1,
                        relayCsr = ?2,
                        relayCertRequestedAt = ?1,
                        relayCertAttempts = 1,
                        relayCertSigned = false,
                        relayStopped = false
                        where id = ?3
                        """,
                new Date(),
                csr,
                id
        );
        return updated == 1;
    }

    @Transactional
    public boolean retryRelayCertRequestTransactional(Long id) {
        int updated = update(
                """
                        updatedAt = ?1,
                        relayCertRequestedAt = ?1,
                        relayCertAttempts = coalesce(relayCertAttempts, 0) + 1
                        where id = ?2
                        """,
                new Date(),
                id
        );
        return updated == 1;
    }

    /**
     * Marks the relay certificate of the step as received. Returns false if it was already marked (e.g. the
     * answer to a retried request arrives as well) or the step does not wait for a certificate.
     */
    @Transactional
    public boolean claimRelayCertSignedTransactional(Long id) {
        int updated = update(
                """
                        updatedAt = ?1,
                        relayCertSigned = true
                        where id = ?2
                          and relayCsr is not null
                          and (relayCertSigned is null or relayCertSigned = false)
                        """,
                new Date(),
                id
        );
        return updated == 1;
    }

    @Transactional
    public boolean setRelayStoppedTransactional(Long id) {
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
