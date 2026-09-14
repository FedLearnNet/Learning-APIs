package bio.cosy.feddb.local.api.statistics.request;

import bio.cosy.feddb.core.base.BaseEntity;
import bio.cosy.feddb.local.api.learning.request.FederatedLearningRequestStatus;
import bio.cosy.feddb.local.api.query.QueryEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;
import java.util.UUID;

@Entity
@Table(name = "request_data_statistics",
        indexes = {
                @Index(name = "idx_request_data_statistics_query_id", columnList = "query_id")
        })
@Getter
@Setter
public class RequestDataStatisticsEntity extends BaseEntity {

    @Column(name = "request_keycloak_id", length = 255, nullable = false)
    private String requestKeycloakId;

    @Column(name = "verified_on")
    private Date verifiedOn;

    @Column(name = "request_id")
    private UUID requestId;

    @Column(name = "verified_by_keycloak_id", length = 255)
    private String verifiedByKeycloakId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private FederatedLearningRequestStatus status = FederatedLearningRequestStatus.PENDING;

    @ManyToOne(cascade = CascadeType.REFRESH)
    @JoinColumn(name = "query_id")
    private QueryEntity query;
}
