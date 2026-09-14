package bio.cosy.feddb.local.api.cohort.inclusion;

import bio.cosy.feddb.core.api.query.QueryOperatorDTO;
import bio.cosy.feddb.core.base.BaseEntity;
import bio.cosy.feddb.local.api.cohort.CohortEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.envers.Audited;
import org.hibernate.type.SqlTypes;

import java.util.List;

@Entity
@Table(name = "cohort_criteria",
        indexes = {
                @Index(name = "idx_cohort_criteria_cohort_id", columnList = "cohort_id")
        })
@Audited
@Getter
@Setter
public class CohortCriterionEntity extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cohort_id", nullable = false)
    private CohortEntity cohort;

    @Enumerated(EnumType.STRING)
    @Column(name = "type")
    private CohortCriterionType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "variable_role")
    private EvidenceVariableRole variableRole;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "ontology_id")
    private String ontologyId;

    @Column(name = "data_type_id")
    private String dataTypeId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "operator", columnDefinition = "jsonb")
    private List<QueryOperatorDTO> operator;
}
