package de.unihamburg.daibetes.api.development.run;

import bio.cosy.feddb.core.execution.event.ExecutionEventPublisher;
import bio.cosy.feddb.core.execution.message.ExecutionMessageRoundDTO;
import bio.cosy.feddb.core.execution.run.dto.FederatedRunLifecycleDTO;
import bio.cosy.feddb.core.execution.run.dto.UpdateRunDTO;
import de.unihamburg.daibetes.api.development.run.federated.FederatedParticipantUpdateDTO;
import de.unihamburg.daibetes.api.development.run.federated.FederatedRoundMessageCreateDTO;
import de.unihamburg.daibetes.execution.attempt.ExecutionAttemptAO;
import de.unihamburg.daibetes.execution.attempt.ExecutionAttemptEntity;
import de.unihamburg.daibetes.execution.attempt.ExecutionAttemptLifecycle;
import de.unihamburg.daibetes.execution.federation.ExecutionFederationAO;
import de.unihamburg.daibetes.execution.federation.ExecutionFederationMapper;
import de.unihamburg.daibetes.execution.step.ExecutionRunStepEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;


@ApplicationScoped
public class TestRunProgressBO {

    @Inject
    ExecutionAttemptLifecycle lifecycle;

    @Inject
    ExecutionAttemptAO attemptAO;

    @Inject
    ExecutionFederationAO federationAO;

    @Inject
    ExecutionEventPublisher events;

    @Inject
    ExecutionFederationMapper federationMapper;

    public ExecutionMessageRoundDTO storeRound(Long attemptId, FederatedRoundMessageCreateDTO create) {
        ExecutionMessageRoundDTO round = federationMapper.createDtoToRoundDTO(create);
        lifecycle.storeRoundTransactional(attemptId, round);
        return round;
    }

    public void updateFederatedRun(ExecutionRunStepEntity step, FederatedRunLifecycleDTO update) {
        federationAO.updateCurrentRound(step.getFederation(), update.getCurrentRound());
        events.stepChanged(step.getId());
    }

    public void updateParticipant(ExecutionAttemptEntity attempt, FederatedParticipantUpdateDTO update) {
        attemptAO.applyParticipantUpdate(attempt, update);
        if (update.getStatus() != null) {
            lifecycle.updateTransactional(attempt.getId(), new UpdateRunDTO(attempt.getId(), update.getStatus(), null));
        } else {
            events.attemptUpdated(attempt.getId());
        }
    }
}
