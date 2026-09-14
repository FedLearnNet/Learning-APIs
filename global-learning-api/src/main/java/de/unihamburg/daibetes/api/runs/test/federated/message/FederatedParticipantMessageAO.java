package de.unihamburg.daibetes.api.runs.test.federated.message;

import bio.cosy.feddb.core.api.run.message.RunMessageTypes;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

@ApplicationScoped
public class FederatedParticipantMessageAO implements PanacheRepository<FederatedParticipantMessageEntity> {

    public List<FederatedParticipantMessageEntity> findByParticipant(Long participantId) {
        return list("participant.id = ?1",
                Sort.by("createdAt", Sort.Direction.Ascending),
                participantId);
    }

    public List<FederatedParticipantMessageEntity> findByParticipant(Long participantId, RunMessageTypes type) {
        return list("participant.id = ?1 and type = ?2",
                Sort.by("createdAt", Sort.Direction.Descending),
                participantId, type);
    }

}
