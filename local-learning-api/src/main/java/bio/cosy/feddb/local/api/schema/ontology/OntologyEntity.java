package bio.cosy.feddb.local.api.schema.ontology;

import bio.cosy.feddb.core.base.BaseEntity;
import bio.cosy.feddb.local.api.schema.schemanode.SchemaNodeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.OneToMany;
import jakarta.persistence.CascadeType;

import java.util.HashSet;
import java.util.Set;

import org.hibernate.envers.Audited;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Entity(name = "ontology")
@Table(name = "ontology")
@Getter
@Setter
@Audited
public class OntologyEntity extends BaseEntity {
    @Column(name = "name", columnDefinition = "TEXT", nullable = false)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "global_id", nullable = false, unique = true)
    private String globalId;

    @Column(name = "root_source", columnDefinition = "TEXT")
    private String rootSource; // This is the source of the ontology, if applicable

    // Bidirectional relationship with SchemaNodeEntity
    // As a deleted ontology breaks the schema we cascade up any changes
    @OneToMany(mappedBy = "ontology", cascade = {CascadeType.ALL}, orphanRemoval = true)
    private Set<SchemaNodeEntity> schemaNodes = new HashSet<>();

    @Override
    public String toString() {
        return "OntologyEntity{" +
                "rootSource='" + rootSource + '\'' +
                ", globalId='" + globalId + '\'' +
                ", description='" + description + '\'' +
                ", name='" + name + '\'' +
                '}';
    }
}
