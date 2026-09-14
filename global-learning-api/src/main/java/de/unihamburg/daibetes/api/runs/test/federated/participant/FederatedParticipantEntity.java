package de.unihamburg.daibetes.api.runs.test.federated.participant;

import bio.cosy.feddb.core.api.run.FederatedParticipantType;
import bio.cosy.feddb.core.api.run.RunStatusTypes;
import bio.cosy.feddb.core.base.BaseEntity;
import de.unihamburg.daibetes.api.runs.test.federated.FederatedTestRunEntity;
import de.unihamburg.daibetes.api.runs.test.federated.message.FederatedParticipantMessageEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

@Setter
@Getter
@Entity
@Table(name = "federated_run_participants")
public class FederatedParticipantEntity extends BaseEntity {

    @Column(name = "participant_id", nullable = false)
    private String participantId;

    @Enumerated(EnumType.STRING)
    private FederatedParticipantType role;

    @Enumerated(EnumType.STRING)
    private RunStatusTypes status;

    @Column(name = "current_round")
    private Integer currentRound;

    @Column(name = "messages_received")
    private Integer messagesReceived;

    @Column(name = "messages_sent")
    private Integer messagesSent;

    /**
     * JSON array of participantIds the participant is currently waiting on.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "waiting_for", columnDefinition = "jsonb")
    private List<String> waitingFor = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "hyper_params", columnDefinition = "jsonb")
    private LinkedHashMap<String, Object> hyperParams;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "config", columnDefinition = "jsonb")
    private FederatedParticipantConfigDTO config;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "input_file_paths", columnDefinition = "jsonb")
    private LinkedHashMap<String, String> inputFilePaths;

    @ManyToOne(cascade = CascadeType.REFRESH)
    @JoinColumn(name = "federated_run_id", nullable = false)
    private FederatedTestRunEntity run;

    @OneToMany(mappedBy = "participant", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt ASC")
    private Set<FederatedParticipantMessageEntity> messages;
}
