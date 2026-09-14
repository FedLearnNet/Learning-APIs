package bio.cosy.feddb.local.api.cohort.queryability;

import org.hibernate.envers.Audited;

import bio.cosy.feddb.core.base.BaseEntity;
import bio.cosy.feddb.local.api.cohort.CohortEntity;
import bio.cosy.feddb.local.api.schema.schemanode.SchemaNodeEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "cohort_queryability",
        uniqueConstraints = @UniqueConstraint(columnNames = {"cohort_id", "schema_node_id"}),
        indexes = {
                @Index(name = "idx_cohort_queryability_schema_node_id", columnList = "schema_node_id")
        })
@Getter
@Setter
@Audited
public class CohortQueryAbilityEntity extends BaseEntity {
    @ManyToOne(cascade = {CascadeType.REFRESH})
    @JoinColumn(name = "schema_node_id", nullable = false)
    private SchemaNodeEntity schemaNode;

    @ManyToOne(cascade = {CascadeType.REFRESH})
    @JoinColumn(name = "cohort_id", nullable = false)
    private CohortEntity cohort;

    @Enumerated(EnumType.STRING)
    @Column(name = "queryability_info", length = 12, nullable = false)
    private QueryAbility queryAbilityInfo = QueryAbility.VALUE;
}
