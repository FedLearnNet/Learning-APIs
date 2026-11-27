package bio.cosy.feddb.local.api.query;

import bio.cosy.feddb.core.api.query.item.QueryItemDTO;
import bio.cosy.feddb.core.base.BaseEntity;
import bio.cosy.feddb.local.api.cohort.patient.traceability.query.QueryPatientEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "queries")
@Getter
@Setter
public class QueryEntity extends BaseEntity {

    @Column(name = "global_unique_query_id", nullable = false, unique = true)
    private String globalQueryId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "query_string", columnDefinition = "jsonb")
    private QueryItemDTO query;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private QueryStatusEnum status = QueryStatusEnum.UNKNOWN;

    @Column(name = "status_message", columnDefinition = "TEXT") // 1GB with PostgreSQL, good enough
    private String statusMessage;

    @Column(name = "received_at")
    private LocalDateTime receivedAt;

    @Column(name = "keycloak_id")
    private String keycloakId;

    @ElementCollection
    @CollectionTable(
            name = "query_queried_cohorts",
            joinColumns = @JoinColumn(name = "query_id"),
            indexes = {
                    @Index(name = "idx_query_queried_cohorts_query_id", columnList = "query_id"),
                    @Index(name = "idx_query_queried_cohorts_cohort_id", columnList = "cohort_id")
            })
    @Column(name = "cohort_id")
    private Set<Long> queriedCohortIds = new HashSet<>();

    @OneToMany(mappedBy = "query", cascade = CascadeType.ALL)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Set<QueryPatientEntity> patients;
}
