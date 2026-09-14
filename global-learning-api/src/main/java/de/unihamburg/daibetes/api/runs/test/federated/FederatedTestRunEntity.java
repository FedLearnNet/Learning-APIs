package de.unihamburg.daibetes.api.runs.test.federated;

import bio.cosy.feddb.core.api.run.RunStatusTypes;
import bio.cosy.feddb.core.base.BaseEntity;
import de.unihamburg.daibetes.api.app.version.FederatedAppVersionEntity;
import de.unihamburg.daibetes.api.runs.test.federated.message.FederatedRoundMessageEntity;
import de.unihamburg.daibetes.api.runs.test.federated.participant.FederatedParticipantEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.LinkedHashMap;
import java.util.Set;

@Setter
@Getter
@Entity
@Table(name = "federated_test_runs")
public class FederatedTestRunEntity extends BaseEntity {

    @Enumerated(EnumType.STRING)
    private RunStatusTypes status;

    @Column(columnDefinition = "TEXT")
    private String error;

    @Column(name = "aggregator_id")
    private String aggregatorId;

    @Column(name = "current_round")
    private Integer currentRound;

    @Column(name = "total_rounds")
    private Integer totalRounds;

    @Column(name = "start_aggregator")
    private Boolean startAggregator;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "config", columnDefinition = "jsonb")
    private FederatedTestRunConfigDTO config;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "output_data", columnDefinition = "jsonb")
    private LinkedHashMap<String, Object> outputData;

    @ManyToOne(cascade = CascadeType.REFRESH)
    @JoinColumn(name = "federated_app_version_id", nullable = false)
    private FederatedAppVersionEntity federatedAppVersion;

    @OneToMany(mappedBy = "run", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<FederatedParticipantEntity> participants;

    @OneToMany(mappedBy = "run", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<FederatedRoundMessageEntity> roundMessages;
}
