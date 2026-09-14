package bio.cosy.feddb.local.api.learning.project.run.step;

import bio.cosy.feddb.core.api.socket.FederatedLearningRelayInfoDTO;
import bio.cosy.feddb.core.api.workflow.base.step.BaseWorkflowStepAO;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.util.Date;
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
}
