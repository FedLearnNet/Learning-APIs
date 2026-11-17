package de.unihamburg.daibetes.api.project.training.result;

import bio.cosy.feddb.core.base.BaseEntity;
import de.unihamburg.daibetes.execution.experiment.ExecutionExperimentEntity;
import de.unihamburg.daibetes.api.project.training.result.response.RunResultResponseEntity;
import bio.cosy.feddb.core.api.socket.RunResultRequestType;
import jakarta.persistence.*;
import org.hibernate.annotations.ColumnDefault;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "run_metrics_request")
@Getter
@Setter
public class RunResultRequestEntity extends BaseEntity {

    @Column(name = "global_request_id", unique = true, nullable = false)
    private UUID globalRequestId;

    @Column(name = "request_keycloak_id", nullable = false)
    private String requestKeycloakId;

    @Enumerated(EnumType.STRING)
    @ColumnDefault("'METRICS'")
    @Column(name = "type", length = 20, nullable = false)
    private RunResultRequestType type = RunResultRequestType.METRICS;

    @ManyToOne(cascade = CascadeType.REFRESH)
    @JoinColumn(name = "experiment_id", nullable = false)
    private ExecutionExperimentEntity experiment;

    @OneToMany(mappedBy = "request",
            orphanRemoval = true,
            cascade = {CascadeType.ALL})
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Set<RunResultResponseEntity> responses;
}
