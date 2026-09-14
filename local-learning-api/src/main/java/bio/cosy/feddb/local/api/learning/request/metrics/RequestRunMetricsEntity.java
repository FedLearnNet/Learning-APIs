package bio.cosy.feddb.local.api.learning.request.metrics;

import bio.cosy.feddb.core.base.BaseEntity;
import bio.cosy.feddb.local.api.learning.project.FederatedLearningProjectEntity;
import bio.cosy.feddb.local.api.learning.request.FederatedLearningRequestStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;
import java.util.UUID;

@Entity
@Table(name = "request_run_metrics",
        indexes = {
                @Index(name = "idx_request_run_metrics_project_id", columnList = "project_id")
        })
@Getter
@Setter
public class RequestRunMetricsEntity extends BaseEntity {

    @Column(name = "request_keycloak_id", length = 255, nullable = false)
    private String requestKeycloakId;

    @Column(name = "global_request_id", nullable = false)
    private UUID globalRequestId;

    @Column(name = "verified_on")
    private Date verifiedOn;

    @Column(name = "verified_by_keycloak_id", length = 255)
    private String verifiedByKeycloakId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private FederatedLearningRequestStatus status = FederatedLearningRequestStatus.PENDING;

    @ManyToOne(cascade = CascadeType.REFRESH)
    @JoinColumn(name = "project_id", nullable = false)
    private FederatedLearningProjectEntity project;
}
