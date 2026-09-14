package de.unihamburg.daibetes.api.project.experiment.federated.metrics;

import bio.cosy.feddb.core.base.BaseEntity;
import de.unihamburg.daibetes.api.project.experiment.federated.ProjectFederatedExperimentEntity;
import de.unihamburg.daibetes.api.project.experiment.federated.metrics.response.RunMetricsResponseEntity;
import jakarta.persistence.*;
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
public class RunMetricsRequestEntity extends BaseEntity {

    @Column(name = "global_request_id", unique = true, nullable = false)
    private UUID globalRequestId;

    @Column(name = "request_keycloak_id", nullable = false)
    private String requestKeycloakId;

    @ManyToOne(cascade = CascadeType.REFRESH)
    @JoinColumn(name = "experiment_id", nullable = false)
    private ProjectFederatedExperimentEntity experiment;

    @OneToMany(mappedBy = "request",
            orphanRemoval = true,
            cascade = {CascadeType.ALL})
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Set<RunMetricsResponseEntity> responses;
}
