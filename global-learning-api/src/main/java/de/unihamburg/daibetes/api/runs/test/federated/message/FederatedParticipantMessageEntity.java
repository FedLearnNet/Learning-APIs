package de.unihamburg.daibetes.api.runs.test.federated.message;

import bio.cosy.feddb.core.api.run.message.RunMessageTypes;
import bio.cosy.feddb.core.base.BaseEntity;
import de.unihamburg.daibetes.api.runs.test.federated.participant.FederatedParticipantEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@Entity
@Table(name = "federated_participant_messages")
public class FederatedParticipantMessageEntity extends BaseEntity {

    private String process;

    @Enumerated(EnumType.STRING)
    private RunMessageTypes type;

    @Column(columnDefinition = "TEXT")
    private String message;

    @ManyToOne(cascade = CascadeType.REFRESH)
    @JoinColumn(name = "participant_id", nullable = false)
    private FederatedParticipantEntity participant;
}
