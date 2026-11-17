package de.unihamburg.daibetes.execution.attempt;

import bio.cosy.feddb.core.execution.ExecutionPurpose;
import bio.cosy.feddb.core.execution.ExecutionMode;
import bio.cosy.feddb.core.execution.attempt.BaseExecutionAttemptAO;
import bio.cosy.feddb.core.execution.run.types.RunStatusTypes;
import de.unihamburg.daibetes.api.development.run.federated.FederatedParticipantUpdateDTO;
import de.unihamburg.daibetes.execution.federation.ExecutionFederatedAttemptEntity;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class ExecutionAttemptAO extends BaseExecutionAttemptAO<ExecutionAttemptEntity> {

    public List<ExecutionAttemptEntity> listPendingTestAttemptsOfApp(Long appId) {
        return list("status = ?1 and step.run.purpose = ?2 and step.federatedAppVersion.federatedApp.id = ?3",
                Sort.by("id"), RunStatusTypes.PENDING, ExecutionPurpose.TEST, appId);
    }

    public Optional<ExecutionAttemptEntity> findTestAttemptOptional(Long attemptId) {
        return find("id = ?1 and step.run.purpose = ?2", attemptId, ExecutionPurpose.TEST).firstResultOptional();
    }

    // Only the id, so the experiment of a platform aggregator is locked before its attempt is loaded.
    public Optional<Long> findAggregatorExperimentIdOptional(Long attemptId) {
        return find("select step.run.experiment.id from ExecutionAttemptEntity where id = ?1 and federation.relayConfig is not null",
                attemptId).project(Long.class).firstResultOptional();
    }

    // Only the id is read before the lock, so no unlocked version of the attempt ends up managed.
    public Optional<ExecutionAttemptEntity> findParticipantAttemptWrite(Long runId, String participantId) {
        return find("select id from ExecutionAttemptEntity where step.run.id = ?1 and federation.participant.participantId = ?2",
                runId, participantId).project(Long.class).firstResultOptional().flatMap(this::findByIdWrite);
    }

    public Optional<ExecutionAttemptEntity> findSelectedTestAttemptWrite(Long runId) {
        return find("select selectedAttempt.id from ExecutionRunStepEntity where run.id = ?1 and run.purpose = ?2 and run.mode = ?3",
                runId, ExecutionPurpose.TEST, ExecutionMode.FEDERATED).project(Long.class).firstResultOptional().flatMap(this::findByIdWrite);
    }

    public void applyParticipantUpdate(ExecutionAttemptEntity attempt, FederatedParticipantUpdateDTO update) {
        ExecutionFederatedAttemptEntity federation = attempt.federationOrNew();
        Optional.ofNullable(update.getCurrentRound()).ifPresent(federation::setCurrentRound);
        Optional.ofNullable(update.getMessagesReceived()).ifPresent(federation::setMessagesReceived);
        Optional.ofNullable(update.getMessagesSent()).ifPresent(federation::setMessagesSent);
        Optional.ofNullable(update.getWaitingFor()).ifPresent(waitingFor -> federation.setWaitingFor(new ArrayList<>(waitingFor)));
    }
}
