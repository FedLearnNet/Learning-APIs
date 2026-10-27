package bio.cosy.feddb.local.execution.federation;

import bio.cosy.feddb.core.base.BaseEntity;
import bio.cosy.feddb.core.execution.run.dto.FederatedRunConfigDTO;
import bio.cosy.feddb.local.execution.step.ExecutionRunStepEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/** How this clinic takes part in a step across clinics: its relay client, and the controller of old FeatureCloud apps. */
@Setter
@Getter
@Entity
@Table(name = "execution_federation")
public class ExecutionFederationEntity extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "step_id", nullable = false, unique = true, updatable = false)
    private ExecutionRunStepEntity step;

    // Relay channel and keys of this clinic; holds secrets, never sent to a frontend.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "config", columnDefinition = "jsonb")
    private FederatedRunConfigDTO config;

    // This clinic coordinates the step and runs its aggregator.
    @Column(name = "start_aggregator")
    private Boolean startAggregator;

    // Certificate signing request of the controller run; the controller only joins the relay once it is signed.
    @Column(name = "relay_csr", columnDefinition = "TEXT")
    private String relayCsr;

    @Column(name = "relay_cert_requested_at")
    private Instant relayCertRequestedAt;

    @Column(name = "relay_cert_attempts")
    private Integer relayCertAttempts;

    @Column(name = "relay_cert_signed")
    private Boolean relayCertSigned;

    // True once the controller run of the step was stopped.
    @Column(name = "relay_stopped")
    private Boolean relayStopped;
}
