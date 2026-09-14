package bio.cosy.feddb.local.api.cohort.statistics.entry;

import bio.cosy.feddb.core.base.BaseEntity;
import bio.cosy.feddb.local.api.cohort.statistics.dashboard.CustomStatisticsDashboardEntity;
import bio.cosy.feddb.local.api.cohort.statistics.entry.config.CustomStatisticConfig;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "custom_statistic",
        indexes = {
                @Index(name = "idx_custom_statistic_dashboard_id", columnList = "dashboard_id")
        })
@Getter
@Setter
public class CustomStatisticEntity extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "dashboard_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private CustomStatisticsDashboardEntity dashboard;

    @Column(name = "name", length = 120, nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", length = 32, nullable = false)
    private CustomStatisticType type;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "config", columnDefinition = "jsonb", nullable = false)
    private CustomStatisticConfig config;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 0;
}
