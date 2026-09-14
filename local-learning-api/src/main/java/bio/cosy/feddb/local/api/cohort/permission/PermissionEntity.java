package bio.cosy.feddb.local.api.cohort.permission;

import org.hibernate.envers.Audited;

import bio.cosy.feddb.core.base.BaseEntity;
import bio.cosy.feddb.local.api.cohort.CohortEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Entity(name = "permission")
@Table(name = "permission",
        indexes = {
                @Index(name = "idx_permission_cohort_id", columnList = "cohort_id")
        })
@Getter
@Setter
@Audited
public class PermissionEntity extends BaseEntity {

    @Column(name = "user_id", length = 255)
    private String userId;

    @Column(name = "query_retry_time")
    private Integer queryRetryTime;

    @Column(name = "is_allowed_to_query", nullable = false)
    private Boolean isAllowedToQuery;

    @Column(name = "query_sample_threshold")
    private Integer querySampleThreshold;

    @Enumerated(EnumType.STRING)
    @Column(name = "auto_training_access")
    private AutoTrainingAccess autoTrainingAccess;

    @Enumerated(EnumType.STRING)
    @Column(name = "auto_statistics_access")
    private AutoStatisticsAccess autoStatisticsAccess;

    @Enumerated(EnumType.STRING)
    @Column(name = "auto_metrics_access")
    private AutoMetricsAccess autoMetricsAccess;

    @Column(name = "valid_from")
    private LocalDate validFrom;

    @Column(name = "valid_until")
    private LocalDate validUntil;

    @ManyToOne(cascade = {CascadeType.REFRESH, CascadeType.REFRESH})
    @JoinColumn(name = "cohort_id", nullable = false)
    private CohortEntity cohort;
}
