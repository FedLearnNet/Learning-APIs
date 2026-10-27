package de.unihamburg.daibetes.execution.federation;

import bio.cosy.feddb.core.base.BaseEntity;
import bio.cosy.feddb.core.execution.run.dto.FederatedRunConfigDTO;
import de.unihamburg.daibetes.execution.step.ExecutionRunStepEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.LinkedHashMap;
import java.util.Map;

@Setter
@Getter
@Entity
@Table(name = "execution_federation")
public class ExecutionFederationEntity extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "step_id", nullable = false, unique = true, updatable = false)
    private ExecutionRunStepEntity step;

    @Column(name = "total_rounds")
    private Integer totalRounds;

    @Column(name = "current_round")
    private Integer currentRound = 0;

    @Column(name = "start_aggregator")
    private Boolean startAggregator;

    // Relay channel, keys and controller polling; holds secrets, never sent to a frontend as it is.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "config", columnDefinition = "jsonb")
    private FederatedRunConfigDTO config;

    // Relay client id per participant, so a certificate is only signed for the id a participant owns.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "relay_client_ids", columnDefinition = "jsonb")
    private Map<String, String> relayClientIds = new LinkedHashMap<>();

    // True once the relay run of the step was stopped on the relay server.
    @Column(name = "relay_stopped")
    private Boolean relayStopped;
}
