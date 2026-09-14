package bio.cosy.feddb.local.api.schema.schemanode;

import bio.cosy.feddb.core.api.datamodler.schema.SchemaNodeType;
import bio.cosy.feddb.core.base.BaseEntity;
import bio.cosy.feddb.local.api.cohort.CohortEntity;
import bio.cosy.feddb.local.api.cohort.patient.dataentry.PatientDataEntryEntity;
import bio.cosy.feddb.local.api.schema.datatype.DataTypeEntity;
import bio.cosy.feddb.local.api.schema.ontology.OntologyEntity;
import io.smallrye.common.constraint.NotNull;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.envers.Audited;

import java.util.HashSet;
import java.util.Set;

/**
 * Represents a schema for a cohort, which defines the data structure of the cohort.
 */
@Entity
@Table(name = "schemanode",
        indexes = {
                @Index(name = "idx_schemanode_cohort_id", columnList = "cohort_id"),
                @Index(name = "idx_schemanode_parent_id", columnList = "parent_id"),
                @Index(name = "idx_schemanode_data_type_id", columnList = "data_type_id"),
                @Index(name = "idx_schemanode_ontology_id", columnList = "ontology_id"),
                @Index(name = "idx_schemanode_data_type_id_ontology_id", columnList = "data_type_id, ontology_id")
        })
@Getter
@Setter
@Audited
public class SchemaNodeEntity extends BaseEntity {
    // Each cohort has multiple schema nodes, and each schema node can be associated with
    // multiple cohorts.

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cohort_id")
    private CohortEntity cohort;

    @Column(name = "global_id", nullable = false)
    private String globalId;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "depth")
    @NotNull
    private Long depth;
    @NotBlank(message = "Name cannot be blank")
    @Column(name = "name", length = 1024, nullable = false)
    private String name;

    // column definition text to allow big descriptions
    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "type", length = 255, nullable = false)
    @Enumerated(EnumType.STRING)
    private SchemaNodeType type;

    @ManyToOne(cascade = {CascadeType.REFRESH}, fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id", nullable = true)
    private SchemaNodeEntity parent;

    @OneToMany(cascade = {CascadeType.ALL}, mappedBy = "parent", orphanRemoval = true)
    private Set<SchemaNodeEntity> children = new HashSet<>();

    @ManyToOne(cascade = {CascadeType.REFRESH}, fetch = FetchType.LAZY)
    @JoinColumn(name = "data_type_id", nullable = true)
    private DataTypeEntity dataType;

    @ManyToOne(cascade = {CascadeType.REFRESH}, fetch = FetchType.LAZY)
    @JoinColumn(name = "ontology_id", nullable = true)
    private OntologyEntity ontology;

    @OneToMany(cascade = {CascadeType.ALL}, mappedBy = "schemaNode")
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Set<PatientDataEntryEntity> dataEntities;

    @Override
    public String toString() {
        return """
                SchemaNodeEntity[
                  id=%d,
                  globalId=%s,
                  name=%s,
                  description=%s,
                ]""".formatted(getId(), globalId, name, description);
    }
}
