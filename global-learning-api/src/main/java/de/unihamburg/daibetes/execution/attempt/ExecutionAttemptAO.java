package de.unihamburg.daibetes.execution.attempt;

import bio.cosy.feddb.core.execution.ExecutionPurpose;
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

    /** Attempts of test runs of an app still waiting for its app engine, oldest first. */
    public List<ExecutionAttemptEntity> listPendingTestAttemptsOfApp(Long appId) {
        return list("status = ?1 and step.run.purpose = ?2 and step.federatedAppVersion.federatedApp.id = ?3",
                Sort.by("id"), RunStatusTypes.PENDING, ExecutionPurpose.TEST, appId);
    }

    public Optional<ExecutionAttemptEntity> findTestAttemptOptional(Long attemptId) {
        return find("id = ?1 and step.run.purpose = ?2", attemptId, ExecutionPurpose.TEST).firstResultOptional();
    }

    public Optional<ExecutionAttemptEntity> findParticipantAttemptOptional(Long runId, String participantId) {
        return find("step.run.id = ?1 and federation.participant.participantId = ?2", runId, participantId).firstResultOptional();
    }

    public void applyParticipantUpdate(ExecutionAttemptEntity attempt, FederatedParticipantUpdateDTO update) {
        ExecutionFederatedAttemptEntity federation = attempt.federationOrNew();
        Optional.ofNullable(update.getCurrentRound()).ifPresent(federation::setCurrentRound);
        Optional.ofNullable(update.getMessagesReceived()).ifPresent(federation::setMessagesReceived);
        Optional.ofNullable(update.getMessagesSent()).ifPresent(federation::setMessagesSent);
        Optional.ofNullable(update.getWaitingFor()).ifPresent(waitingFor -> federation.setWaitingFor(new ArrayList<>(waitingFor)));
    }
}
