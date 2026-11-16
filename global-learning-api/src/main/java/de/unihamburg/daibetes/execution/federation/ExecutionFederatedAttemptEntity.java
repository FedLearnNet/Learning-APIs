package de.unihamburg.daibetes.execution.federation;

import bio.cosy.feddb.core.base.BaseEntity;
import bio.cosy.feddb.core.execution.run.dto.FederatedRunConfigDTO;
import de.unihamburg.daibetes.execution.attempt.ExecutionAttemptEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.util.ArrayList;
import java.util.List;

@Setter
@Getter
@Entity
@Table(name = "execution_federated_attempt")
public class ExecutionFederatedAttemptEntity extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "attempt_id", nullable = false, unique = true, updatable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private ExecutionAttemptEntity attempt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "participant_id")
    private ExecutionParticipantEntity participant;

    @Column(name = "current_round")
    private Integer currentRound = 0;

    @Column(name = "messages_sent")
    private Integer messagesSent = 0;

    @Column(name = "messages_received")
    private Integer messagesReceived = 0;

    // Participant ids it currently waits on.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "waiting_for", columnDefinition = "jsonb")
    private List<String> waitingFor = new ArrayList<>();

    // Relay slot of the platform's own aggregator; a clinic gets its slot through the broadcast instead. Holds secrets.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "relay_config", columnDefinition = "jsonb")
    private FederatedRunConfigDTO relayConfig;
}
