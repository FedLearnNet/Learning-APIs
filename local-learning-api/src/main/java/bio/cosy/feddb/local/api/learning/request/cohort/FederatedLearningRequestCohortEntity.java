package bio.cosy.feddb.local.api.learning.request.cohort;

import bio.cosy.feddb.core.base.BaseEntity;
import bio.cosy.feddb.local.api.cohort.CohortEntity;
import bio.cosy.feddb.local.api.learning.request.FederatedLearningRequestEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * One row per cohort involved in a training request, so owners can decide independently.
 */
@Entity
@Table(name = "federated_learning_request_cohort",
        uniqueConstraints = @UniqueConstraint(columnNames = {"request_id", "cohort_id"}),
        indexes = {
                @Index(name = "idx_fl_request_cohort_request_id", columnList = "request_id"),
                @Index(name = "idx_fl_request_cohort_cohort_id", columnList = "cohort_id")
        })
@Getter
@Setter
public class FederatedLearningRequestCohortEntity extends BaseEntity {

    @ManyToOne(optional = false)
    @JoinColumn(name = "request_id", nullable = false)
    private FederatedLearningRequestEntity request;

    @ManyToOne(optional = false)
    @JoinColumn(name = "cohort_id", nullable = false)
    private CohortEntity cohort;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private FederatedLearningRequestCohortStatus status = FederatedLearningRequestCohortStatus.PENDING;
}
