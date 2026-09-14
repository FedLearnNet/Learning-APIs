package bio.cosy.feddb.local.api.cohort.statistics.dashboard;

import bio.cosy.feddb.core.base.BaseEntity;
import bio.cosy.feddb.local.api.cohort.CohortEntity;
import bio.cosy.feddb.local.api.cohort.statistics.entry.CustomStatisticEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Table(name = "custom_statistics_dashboard",
        indexes = {
                @Index(name = "idx_custom_statistics_dashboard_cohort_id", columnList = "cohort_id")
        })
@Getter
@Setter
public class CustomStatisticsDashboardEntity extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cohort_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private CohortEntity cohort;

    @Column(name = "name", length = 120, nullable = false)
    private String name;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 0;

    @OneToMany(mappedBy = "dashboard", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC, id ASC")
    private Set<CustomStatisticEntity> statistics = new LinkedHashSet<>();
}
