package de.unihamburg.daibetes.api.runs.test.federated.message;

import bio.cosy.feddb.core.base.BaseEntity;
import de.unihamburg.daibetes.api.runs.test.federated.FederatedTestRunEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@Entity
@Table(name = "federated_round_messages")
public class FederatedRoundMessageEntity extends BaseEntity {

    /** "SEND" or "RECEIVE" */
    private String direction;

    @Column(name = "round_nr")
    private Integer round;

    @Column(name = "from_participant")
    private String fromParticipant;

    @Column(name = "to_participant")
    private String toParticipant;

    @Column(name = "communication_id")
    private String communicationId;

    @Column(name = "payload_preview", columnDefinition = "TEXT")
    private String payloadPreview;

    @ManyToOne(cascade = CascadeType.REFRESH)
    @JoinColumn(name = "federated_run_id", nullable = false)
    private FederatedTestRunEntity run;
}
