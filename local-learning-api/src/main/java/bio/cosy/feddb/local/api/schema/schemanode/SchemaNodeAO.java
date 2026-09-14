package bio.cosy.feddb.local.api.schema.schemanode;

import bio.cosy.feddb.core.api.datamodler.schema.SchemaNodeType;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@ApplicationScoped
public class SchemaNodeAO implements PanacheRepository<SchemaNodeEntity> {

    public List<SchemaNodeEntity> findByOntologyGlobalId(String ontologyId) {
        return find("ontology.globalId = ?1", ontologyId).list();
    }

    public List<SchemaNodeEntity> findByOntologyAndDatatypeGlobalId(String ontologyId, String datatypeId) {
        return find("ontology.globalId = ?1 AND dataType.globalDataTypeId = ?2", ontologyId, datatypeId).list();
    }


    public List<SchemaNodeEntity> getSchemaNodesForPatient(Long internalPatientId) {
        return find("SELECT s FROM SchemaNodeEntity s JOIN s.dataEntities.patient p WHERE p.id = ?1", internalPatientId).list();
    }

    public Optional<SchemaNodeEntity> findByIdWithParent(Long id) {
        return find("""
            SELECT s
            FROM SchemaNodeEntity s
            LEFT JOIN FETCH s.parent
            WHERE s.id = ?1
        """, id)
                .firstResultOptional();
    }

    public List<SchemaNodeEntity> getSchemaNodesForCohort(Long cohortId) {
        // Use s.cohort instead of s.cohorts
        return find("cohort.id = ?1", cohortId)
                .list();
    }

    public Optional<SchemaNodeEntity> getRootSchemaNodeForCohort(Long cohortId) {
        return find("cohort.id = ?1 AND type = ?2",
                cohortId, SchemaNodeType.ROOT)
                .firstResultOptional();
    }

    public void deleteReferencedDataTypeIds(Long cohortId) {
        delete("cohort.id = ?1 and dataType is not null", cohortId);
    }

    public void deleteReferencedOntologyIds(Long cohortId) {
        delete("cohort.id = ?1 and ontology is not null", cohortId);
    }

    public List<SchemaNodeEntity> findByIds(Set<Long> schemaNodeIds) {
        return find("id in ?1", schemaNodeIds).list();
    }
}
