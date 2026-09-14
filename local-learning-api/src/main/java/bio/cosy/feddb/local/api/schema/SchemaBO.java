package bio.cosy.feddb.local.api.schema;

import bio.cosy.feddb.core.api.datamodler.schema.SchemaNodeType;
import bio.cosy.feddb.local.api.cohort.CohortEntity;
import bio.cosy.feddb.local.api.schema.datatype.DataTypeBO;
import bio.cosy.feddb.local.api.schema.datatype.DataTypeEntity;
import bio.cosy.feddb.local.api.schema.ontology.OntologyBO;
import bio.cosy.feddb.local.api.schema.ontology.OntologyEntity;
import bio.cosy.feddb.local.api.schema.schemanode.SchemaNodeAO;
import bio.cosy.feddb.local.api.schema.schemanode.SchemaNodeBO;
import bio.cosy.feddb.local.api.schema.schemanode.SchemaNodeEntity;
import bio.cosy.feddb.local.services.datamodler.SchemaServiceBO;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@ApplicationScoped
public class SchemaBO {

    @Inject
    SchemaServiceBO schemaServiceBO;

    @Inject
    SchemaNodeBO schemaNodeBO;

    @Inject
    SchemaNodeAO schemaAO;

    @Inject
    OntologyBO ontologyBO;

    @Inject
    DataTypeBO dataTypeBO;

    public LocalSchemaRootNodeDTO loadRemoteGlobalSchemaById(UUID id) {
        return schemaServiceBO.getWholeSchemaByRootId(id);
    }

    public List<LocalSchemaRootNodeDTO> loadRemoteAllHeads() {
        return schemaServiceBO.getGlobalRootNodes();
    }


    public Set<SchemaNodeEntity> getCohortNodes(Long cohortId, UUID globalSchemaId) {
        // Try to get the nodes of the cohort ID
        List<SchemaNodeEntity> connectedSchemaNodes = schemaAO.getSchemaNodesForCohort(cohortId);
        if (!(connectedSchemaNodes == null || connectedSchemaNodes.isEmpty())) {
            // We have the nodes, so we can return them
            return new HashSet<>(connectedSchemaNodes);
        }
        // We subscribe and query again
        return subscribe(globalSchemaId);
    }

    public Set<SchemaNodeEntity> subscribe(UUID globalSchemaId) {
        // Receives a global schema ID from the global schema service and persists it
        // No cohort relationship is created, that has to be done by the owning side (cohort)

        // globalSchemaId is the ID of the root node, so we can retrieve the root node
        // and work with that
        LocalSchemaRootNodeDTO remoteLoadedSchema = schemaServiceBO.subscribe(globalSchemaId);
        SchemaNodeEntity rootNodeEntity = persistFullSchemaRoot(remoteLoadedSchema);
        return getFullSchema(rootNodeEntity);
    }

    private SchemaNodeEntity persistFullSchemaRoot(LocalSchemaRootNodeDTO remoteLoadedSchema) {
        // Create the local schema root node
        SchemaNodeEntity localRootNode = schemaNodeBO.persistRoot(remoteLoadedSchema);
        // We need to persist to have an ID as this is done by the Database
        persistFullSchema(remoteLoadedSchema.getChildNodes(), localRootNode, 1L);
        return localRootNode;
    }

    private void persistFullSchema(
            Set<LocalSchemaNodeNestedDTO> fields,
            SchemaNodeEntity parent,
            Long depth) {
        // Called on the fields property of the SchemaInfoDTO/SchemaNodeFormInfoFieldEntryDTO
        // Recursively persists all schema nodes, also retrieving relevant datatype and ontology.
        // We keep a list of persisted nodes to be able to undo the persistence if needed
        for (LocalSchemaNodeNestedDTO field : fields) {
            SchemaNodeEntity localSchemaNode = schemaNodeBO.initEntity(field, parent, depth);
            // Keep child reference at parent so that schema can be traversed later
            parent.getChildren().add(localSchemaNode);

            if (localSchemaNode.getType().equals(SchemaNodeType.ATOMIC_ATTRIBUTE) ||
             localSchemaNode.getType().equals(SchemaNodeType.LIST_ATTRIBUTE)) {
                // Ontology
                if (field.getOntology() != null && field.getOntology().getGlobalId() != null) {
                    OntologyEntity localOntology = ontologyBO.fetchOrCreate(field.getOntology());
                    localSchemaNode.setOntology(localOntology);
                    localOntology.getSchemaNodes().add(localSchemaNode);
                }

                // Datatype
                if (field.getDataType() != null && field.getDataType().getGlobalId() != null) {
                    DataTypeEntity localDataType = dataTypeBO.fetchOrCreate(field.getDataType());
                    localSchemaNode.setDataType(localDataType);
                    localDataType.getSchemaNodes().add(localSchemaNode);
                }
            }
            // Done
            schemaAO.persist(localSchemaNode);
            // Recursively persist child nodes
            if (field.getChildNodes() != null && !field.getChildNodes().isEmpty()) {
                persistFullSchema(field.getChildNodes(), localSchemaNode, depth + 1);
            }
        }
    }

    private Set<SchemaNodeEntity> getFullSchema(SchemaNodeEntity nodeEntity) {
        // Returns the full schema starting from the given root node entity
        // This is used to retrieve the schema after it has been persisted
        Set<SchemaNodeEntity> schemaNodes = new HashSet<>();
        schemaNodes.add(nodeEntity);
        if(nodeEntity.getChildren() == null || nodeEntity.getChildren().isEmpty()) {
            return schemaNodes; // No children, return just the root
        }
        // Recursively add all children of the root node
        nodeEntity.getChildren().forEach(childNode -> {
            schemaNodes.addAll(getFullSchema(childNode)); // Recursively get children
        });
        return schemaNodes;
    }
}
