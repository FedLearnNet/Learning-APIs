package de.unihamburg.daibetes.api.runs.test.federated.participant;

import bio.cosy.feddb.core.api.run.RunStatusTypes;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Date;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class FederatedParticipantAO implements PanacheRepository<FederatedParticipantEntity> {

    public List<FederatedParticipantEntity> findByRun(Long runId) {
        return list("run.id = ?1",
                Sort.by("participantId", Sort.Direction.Ascending),
                runId);
    }

    public Optional<FederatedParticipantEntity> findByRunAndParticipant(Long runId, String participantId) {
        return find("run.id = ?1 and participantId = ?2", runId, participantId).firstResultOptional();
    }

    public boolean updateParticipant(Long participantId,
                                     RunStatusTypes status,
                                     Integer currentRound,
                                     Integer messagesReceived,
                                     Integer messagesSent,
                                     List<String> waitingFor) {
        int updated = update(
                """
                        status = ?1,
                        currentRound = ?2,
                        messagesReceived = ?3,
                        messagesSent = ?4,
                        waitingFor = ?5,
                        updatedAt = ?6,
                        version = version + 1
                        where id = ?7
                        """,
                status,
                currentRound,
                messagesReceived,
                messagesSent,
                waitingFor,
                new Date(),
                participantId
        );
        if (updated > 1) {
            throw new IllegalStateException("Multiple participants updated for participantId " + participantId);
        }
        return updated == 1;
    }
}
