package de.unihamburg.daibetes.api.schema;

import bio.cosy.feddb.core.api.datamodler.datatype.DataTypeNodeDTO;
import bio.cosy.feddb.core.api.datamodler.ontology.OntologyNodeDTO;
import bio.cosy.feddb.core.api.datamodler.schema.SchemaEdgeDTO;
import bio.cosy.feddb.core.api.datamodler.schema.SchemaNodeDTO;
import bio.cosy.feddb.core.api.datamodler.schema.SchemaNodeDetailDTO;
import de.unihamburg.daibetes.base.BaseDAO;
import de.unihamburg.daibetes.base.CypherTemplate;
import de.unihamburg.daibetes.helper.InterNodeEdgeLabels;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
import org.neo4j.driver.Value;
import org.neo4j.driver.Values;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@ApplicationScoped
public class SchemaDAO extends BaseDAO<SchemaNodeDTO, SchemaEdgeDTO, SchemaMapper> {

    private static final String PARENT_ID_PARAM = "parentId";
    private static final String ONTOLOGY_ID_PARAM = "ontologyId";
    private static final String DATA_TYPE_ID_PARAM = "dataTypeId";
    // If you change this make sure to also change it in the
    // Neo4jStartupChecker where the unique constraint is created on this property!
    private static final String ROOT_PARENT_MARKER = "__ROOT__";

    public Multi<SchemaNodeDetailDTO> findAllDetail(int page, int pageSize) {
        String cypher = CypherTemplate.of("""
                        MATCH (n:${SCHEMA_LABEL})

                        OPTIONAL MATCH (n)-[:${SCHEMA_TO_ONTOLOGY_REL}]->(o:${ONTO_LABEL})
                        OPTIONAL MATCH (n)-[:${SCHEMA_TO_DATA_TYPE_REL}]->(dt:${DATA_LABEL})
                        OPTIONAL MATCH (parent:${SCHEMA_LABEL})-[:${SUB_SCHEMA_REL}]->(n)
                        OPTIONAL MATCH (n)-[:${SUB_SCHEMA_REL}]->(child:${SCHEMA_LABEL})

                        WITH n,
                             head(collect(DISTINCT o))                      AS ontology,
                             head(collect(DISTINCT dt))                     AS dataType,
                             head(collect(DISTINCT parent.${ID_PROP}))      AS parentId,
                             collect(DISTINCT child.${ID_PROP})             AS childrenIds

                        RETURN
                            n           AS node,
                            ontology    AS ontology,
                            dataType    AS dataType,
                            parentId    AS parentId,
                            childrenIds AS childrenIds
                        SKIP $skip
                        LIMIT $limit
                        """)
                .bind("SCHEMA_LABEL", SchemaNodeDTO.nodeLabel())
                .bind("ONTO_LABEL", OntologyNodeDTO.nodeLabel())
                .bind("DATA_LABEL", DataTypeNodeDTO.nodeLabel())
                .bind("ID_PROP", idPropertyName)
                .bind("SCHEMA_TO_ONTOLOGY_REL", InterNodeEdgeLabels.SCHEMA_TO_ONTOLOGY)
                .bind("SCHEMA_TO_DATA_TYPE_REL", InterNodeEdgeLabels.SCHEMA_TO_DATA_TYPE)
                .bind("SUB_SCHEMA_REL", getEdgeLabel())
                .build();

        return read(
                cypher,
                Values.parameters(
                        "skip", page * pageSize,
                        "limit", pageSize
                ))
                .map(mapper::fromDetailRecord);
    }

    public Uni<SchemaNodeDetailDTO> findDetail(UUID id) {
        if (id == null) {
            return Uni.createFrom().nullItem();
        }

        String cypher = CypherTemplate.of("""
                        MATCH (n:${SCHEMA_LABEL} {${ID_PROP}: $id})

                        OPTIONAL MATCH (n)-[:${SCHEMA_TO_ONTOLOGY_REL}]->(o:${ONTO_LABEL})
                        OPTIONAL MATCH (n)-[:${SCHEMA_TO_DATA_TYPE_REL}]->(dt:${DATA_LABEL})
                        OPTIONAL MATCH (parent:${SCHEMA_LABEL})-[:${SUB_SCHEMA_REL}]->(n)
                        OPTIONAL MATCH (n)-[:${SUB_SCHEMA_REL}]->(child:${SCHEMA_LABEL})

                        WITH n,
                             head(collect(DISTINCT o))                      AS ontology,
                             head(collect(DISTINCT dt))                     AS dataType,
                             head(collect(DISTINCT parent.${ID_PROP}))      AS parentId,
                             collect(DISTINCT child.${ID_PROP})             AS childrenIds

                        RETURN
                            n           AS node,
                            ontology    AS ontology,
                            dataType    AS dataType,
                            parentId    AS parentId,
                            childrenIds AS childrenIds
                        """)
                .bind("SCHEMA_LABEL", SchemaNodeDTO.nodeLabel())
                .bind("ONTO_LABEL", OntologyNodeDTO.nodeLabel())
                .bind("DATA_LABEL", DataTypeNodeDTO.nodeLabel())
                .bind("ID_PROP", idPropertyName)
                .bind("SCHEMA_TO_ONTOLOGY_REL", InterNodeEdgeLabels.SCHEMA_TO_ONTOLOGY)
                .bind("SCHEMA_TO_DATA_TYPE_REL", InterNodeEdgeLabels.SCHEMA_TO_DATA_TYPE)
                .bind("SUB_SCHEMA_REL", getEdgeLabel())
                .build();

        return read(cypher, Values.parameters("id", id.toString()))
                .collect().first()
                .onItem().ifNull().failWith(() -> new NotFoundException("Node not found"))
                .map(record -> record == null ? null : mapper.fromDetailRecord(record));
    }

    public Uni<SchemaNodeDetailDTO> getRootNode(UUID uniqueId) {
        if (uniqueId == null) {
            return Uni.createFrom().nullItem();
        }

        String cypher = CypherTemplate.of("""
                        MATCH (n:${SCHEMA_LABEL} {${ID_PROP}: $unique_id})
                        MATCH path = (n)<-[:${SUB_SCHEMA_REL}*0..]-(root:${SCHEMA_LABEL})
                        WHERE root.isRoot = true

                        OPTIONAL MATCH (root)-[:${SCHEMA_TO_ONTOLOGY_REL}]->(o:${ONTO_LABEL})
                        OPTIONAL MATCH (root)-[:${SCHEMA_TO_DATA_TYPE_REL}]->(dt:${DATA_LABEL})
                        OPTIONAL MATCH (parent:${SCHEMA_LABEL})-[:${SUB_SCHEMA_REL}]->(root)
                        OPTIONAL MATCH (root)-[:${SUB_SCHEMA_REL}]->(child:${SCHEMA_LABEL})

                        WITH root AS node,
                             head(collect(DISTINCT o))                      AS ontology,
                             head(collect(DISTINCT dt))                     AS dataType,
                             head(collect(DISTINCT parent.${ID_PROP}))      AS parentId,
                             collect(DISTINCT child.${ID_PROP})             AS childrenIds

                        RETURN
                            node,
                            ontology,
                            dataType,
                            parentId,
                            childrenIds
                        LIMIT 1
                        """)
                .bind("SCHEMA_LABEL", getNodeLabel())
                .bind("ONTO_LABEL", OntologyNodeDTO.nodeLabel())
                .bind("DATA_LABEL", DataTypeNodeDTO.nodeLabel())
                .bind("ID_PROP", idPropertyName)
                .bind("SUB_SCHEMA_REL", getEdgeLabel())
                .bind("SCHEMA_TO_ONTOLOGY_REL", InterNodeEdgeLabels.SCHEMA_TO_ONTOLOGY)
                .bind("SCHEMA_TO_DATA_TYPE_REL", InterNodeEdgeLabels.SCHEMA_TO_DATA_TYPE)
                .build();

        return read(cypher, Values.parameters("unique_id", uniqueId.toString()))
                .collect().first()
                .onItem().ifNull().failWith(() -> new NotFoundException("Node not found"))
                .map(record -> record == null ? null : mapper.fromDetailRecord(record));
    }

    public Multi<SchemaNodeDetailDTO> getAllRootNodes() {
        String cypher = CypherTemplate.of("""
                        MATCH (root:${SCHEMA_LABEL})
                        WHERE root.isRoot = true

                        OPTIONAL MATCH (root)-[:${SCHEMA_TO_ONTOLOGY_REL}]->(o:${ONTO_LABEL})
                        OPTIONAL MATCH (root)-[:${SCHEMA_TO_DATA_TYPE_REL}]->(dt:${DATA_LABEL})
                        OPTIONAL MATCH (root)-[:${SUB_SCHEMA_REL}]->(child:${SCHEMA_LABEL})

                        WITH root AS node,
                             head(collect(DISTINCT o))                      AS ontology,
                             head(collect(DISTINCT dt))                     AS dataType,
                             collect(DISTINCT child.${ID_PROP})             AS childrenIds

                        RETURN
                            node,
                            ontology,
                            dataType,
                            childrenIds
                        """)
                .bind("SCHEMA_LABEL", getNodeLabel())
                .bind("ONTO_LABEL", OntologyNodeDTO.nodeLabel())
                .bind("DATA_LABEL", DataTypeNodeDTO.nodeLabel())
                .bind("ID_PROP", idPropertyName)
                .bind("SCHEMA_TO_ONTOLOGY_REL", InterNodeEdgeLabels.SCHEMA_TO_ONTOLOGY)
                .bind("SCHEMA_TO_DATA_TYPE_REL", InterNodeEdgeLabels.SCHEMA_TO_DATA_TYPE)
                .bind("SUB_SCHEMA_REL", getEdgeLabel())
                .build();

        return read(cypher)
                .map(mapper::fromDetailRecord);
    }

    public Uni<Boolean> subscribe(UUID uniqueId, String userId) {
        if (uniqueId == null || userId == null) {
            return Uni.createFrom().item(false);
        }

        String cypher = CypherTemplate.of("""
                        MATCH (root:${ROOT_LABEL} {${ID_PROP}: $unique_id})
                        WITH root, coalesce(root.subscriptions, []) AS subs
                        SET root.subscriptions =
                           CASE
                             WHEN $user_id IN subs THEN subs
                             ELSE subs + $user_id
                        END
                        RETURN ($user_id IN root.subscriptions) AS subscribed
                        """)
                .bind("ROOT_LABEL", getNodeLabel())
                .bind("ID_PROP", idPropertyName)
                .build();

        return write(cypher, Values.parameters(
                "unique_id", uniqueId.toString(),
                "user_id", userId
        ))
                .collect().last()
                .onItem().ifNull().failWith(() -> new NotFoundException("Node not found"))
                .map(r -> r.get("subscribed").asBoolean(false));
    }

    public Uni<Boolean> unsubscribe(UUID uniqueId, String userId) {
        if (uniqueId == null || userId == null) {
            return Uni.createFrom().item(false);
        }

        String cypher = CypherTemplate.of("""
                        MATCH (root:${ROOT_LABEL} {${ID_PROP}: $unique_id})
                        WITH root, coalesce(root.subscriptions, []) AS subs
                        SET root.subscriptions =
                           CASE
                            WHEN $user_id IN subs THEN [x IN subs WHERE x <> $user_id]
                            ELSE subs
                        END
                        RETURN NOT ($user_id IN root.subscriptions) AS unsubscribed
                        """)
                .bind("ROOT_LABEL", getNodeLabel())
                .bind("ID_PROP", idPropertyName)
                .build();

        return write(cypher, Values.parameters(
                "unique_id", uniqueId.toString(),
                "user_id", userId
        ))
                .collect().last()
                .onItem().ifNull().failWith(() -> new NotFoundException("Node not found"))
                .map(r -> r.get("unsubscribed").asBoolean(false));
    }

    public Multi<String> getAllChildrenIds(String parentId) {
        if (parentId == null) {
            return Multi.createFrom().empty();
        }

        String cypher = CypherTemplate.of("""
            MATCH (root:${SCHEMA_LABEL} {${ID_PROP}: $unique_id})
            MATCH (root)-[:${SUB_SCHEMA_REL}*0..]->(n:${SCHEMA_LABEL})
            RETURN DISTINCT n.${ID_PROP} AS id
            """)
                .bind("SCHEMA_LABEL", getNodeLabel())
                .bind("ID_PROP", idPropertyName)
                .bind("SUB_SCHEMA_REL", getEdgeLabel())
                .build();

        return read(cypher, Values.parameters("unique_id", parentId))
                .onItem().transform(r -> r.get("id").asString())
                .onCompletion().ifEmpty().failWith(() ->
                        new NotFoundException("Start schema node not found: " + parentId));
    }

    public Multi<SchemaNodeDetailDTO> getChildren(UUID parentId) {
        if (parentId == null) {
            return Multi.createFrom().empty();
        }

        String cypher = CypherTemplate.of("""
                        MATCH (parent:${SCHEMA_LABEL} {${ID_PROP}: $parent_id})
                        MATCH (parent)-[:${SUB_SCHEMA_REL}]->(child:${SCHEMA_LABEL})

                        OPTIONAL MATCH (child)-[:${SCHEMA_TO_ONTOLOGY_REL}]->(o:${ONTO_LABEL})
                        OPTIONAL MATCH (child)-[:${SCHEMA_TO_DATA_TYPE_REL}]->(dt:${DATA_LABEL})
                        OPTIONAL MATCH (p:${SCHEMA_LABEL})-[:${SUB_SCHEMA_REL}]->(child)
                        OPTIONAL MATCH (child)-[:${SUB_SCHEMA_REL}]->(cChild:${SCHEMA_LABEL})

                        WITH child AS node,
                             head(collect(DISTINCT o))                      AS ontology,
                             head(collect(DISTINCT dt))                     AS dataType,
                             head(collect(DISTINCT p.${ID_PROP}))           AS parentId,
                             collect(DISTINCT cChild.${ID_PROP})            AS childrenIds

                        RETURN
                            node,
                            ontology,
                            dataType,
                            parentId,
                            childrenIds
                        """)
                .bind("SCHEMA_LABEL", getNodeLabel())
                .bind("ONTO_LABEL", OntologyNodeDTO.nodeLabel())
                .bind("DATA_LABEL", DataTypeNodeDTO.nodeLabel())
                .bind("ID_PROP", idPropertyName)
                .bind("SUB_SCHEMA_REL", getEdgeLabel())
                .bind("SCHEMA_TO_ONTOLOGY_REL", InterNodeEdgeLabels.SCHEMA_TO_ONTOLOGY)
                .bind("SCHEMA_TO_DATA_TYPE_REL", InterNodeEdgeLabels.SCHEMA_TO_DATA_TYPE)
                .build();

        return read(cypher, Values.parameters("parent_id", parentId.toString()))
                .map(mapper::fromDetailRecord);
    }

    public Multi<SchemaNodeDetailDTO> getAllHeadByOntologyId(UUID ontologyId) {
        if (ontologyId == null) {
            return Multi.createFrom().empty();
        }

        String cypher = CypherTemplate.of("""
                        MATCH (onto:${ONTO_LABEL} {${ID_PROP}: $onto_id})
                        MATCH (s:${SCHEMA_LABEL})-[:${SCHEMA_TO_ONTOLOGY_REL}]->(onto)

                        MATCH (s)<-[:${SUB_SCHEMA_REL}*0..]-(root:${SCHEMA_LABEL})
                        WHERE root.isRoot = true

                        OPTIONAL MATCH (root)-[:${SCHEMA_TO_ONTOLOGY_REL}]->(o:${ONTO_LABEL})
                        OPTIONAL MATCH (root)-[:${SCHEMA_TO_DATA_TYPE_REL}]->(dt:${DATA_LABEL})
                        OPTIONAL MATCH (p:${SCHEMA_LABEL})-[:${SUB_SCHEMA_REL}]->(root)
                        OPTIONAL MATCH (root)-[:${SUB_SCHEMA_REL}]->(child:${SCHEMA_LABEL})

                        WITH root AS node,
                             head(collect(DISTINCT o))                      AS ontology,
                             head(collect(DISTINCT dt))                     AS dataType,
                             head(collect(DISTINCT p.${ID_PROP}))           AS parentId,
                             collect(DISTINCT child.${ID_PROP})             AS childrenIds

                        RETURN
                            node,
                            ontology,
                            dataType,
                            parentId,
                            childrenIds
                        """)
                .bind("SCHEMA_LABEL", getNodeLabel())
                .bind("ONTO_LABEL", OntologyNodeDTO.nodeLabel())
                .bind("DATA_LABEL", DataTypeNodeDTO.nodeLabel())
                .bind("ID_PROP", idPropertyName)
                .bind("SCHEMA_TO_ONTOLOGY_REL", InterNodeEdgeLabels.SCHEMA_TO_ONTOLOGY)
                .bind("SCHEMA_TO_DATA_TYPE_REL", InterNodeEdgeLabels.SCHEMA_TO_DATA_TYPE)
                .bind("SUB_SCHEMA_REL", getEdgeLabel())
                .build();

        return read(cypher, Values.parameters("onto_id", ontologyId.toString()))
                .map(mapper::fromDetailRecord);
    }


    public Uni<Void> updateEdges(SchemaNodeDTO dto) {
        UUID id = dto.getId();
        if (id == null) {
            return Uni.createFrom().failure(
                    new BadRequestException("SchemaNode id must not be null for edge update"));
        }

        String cypher = CypherTemplate.of("""
                        MATCH (n:${SCHEMA_LABEL} {${ID_PROP}: $id})

                        OPTIONAL MATCH (p:${SCHEMA_LABEL})-[pr:${SUB_SCHEMA_REL}]->(n)
                        DELETE pr

                        // delete existing children relations
                        WITH n
                        OPTIONAL MATCH (n)-[cr:${SUB_SCHEMA_REL}]->(:${SCHEMA_LABEL})
                        DELETE cr

                        WITH n
                        OPTIONAL MATCH (n)-[or:${SCHEMA_TO_ONTOLOGY_REL}]->(:${ONTO_LABEL})
                        DELETE or

                        WITH n
                        OPTIONAL MATCH (n)-[dr:${SCHEMA_TO_DATA_TYPE_REL}]->(:${DATA_LABEL})
                        DELETE dr

                        WITH n

                        FOREACH (pid IN CASE WHEN $parentId IS NULL THEN [] ELSE [$parentId] END |
                            MERGE (p:${SCHEMA_LABEL} {${ID_PROP}: pid})
                            MERGE (p)-[:${SUB_SCHEMA_REL}]->(n)
                        )

                        FOREACH (cid IN $childrenIds |
                            MERGE (c:${SCHEMA_LABEL} {${ID_PROP}: cid})
                            MERGE (n)-[:${SUB_SCHEMA_REL}]->(c)
                        )

                        FOREACH (oid IN CASE WHEN $ontologyId IS NULL THEN [] ELSE [$ontologyId] END |
                            MERGE (o:${ONTO_LABEL} {${ID_PROP}: oid})
                            MERGE (n)-[:${SCHEMA_TO_ONTOLOGY_REL}]->(o)
                        )

                        FOREACH (did IN CASE WHEN $dataTypeId IS NULL THEN [] ELSE [$dataTypeId] END |
                            MERGE (dt:${DATA_LABEL} {${ID_PROP}: did})
                            MERGE (n)-[:${SCHEMA_TO_DATA_TYPE_REL}]->(dt)
                        )

                        SET n.${NODE_HASH_PROP} = $nodeHash

                        RETURN n
                        """)
                .bind("SCHEMA_LABEL", SchemaNodeDTO.nodeLabel())
                .bind("ONTO_LABEL", OntologyNodeDTO.nodeLabel())
                .bind("DATA_LABEL", DataTypeNodeDTO.nodeLabel())
                .bind("ID_PROP", idPropertyName)
                .bind("NODE_HASH_PROP", NODE_HASH_PROPERTY)
                .bind("SUB_SCHEMA_REL", getEdgeLabel())
                .bind("SCHEMA_TO_ONTOLOGY_REL", InterNodeEdgeLabels.SCHEMA_TO_ONTOLOGY)
                .bind("SCHEMA_TO_DATA_TYPE_REL", InterNodeEdgeLabels.SCHEMA_TO_DATA_TYPE)
                .build();

        String parentId = dto.getParentId() != null
                ? dto.getParentId().toString()
                : null;

        List<String> childrenIds = dto.getChildrenIds() != null
                ? dto.getChildrenIds().stream()
                .filter(Objects::nonNull)
                .map(UUID::toString)
                .toList()
                : List.of();

        String ontologyId = dto.getOntologyId() != null
                ? dto.getOntologyId().toString()
                : null;

        String dataTypeId = dto.getDataTypeId() != null
                ? dto.getDataTypeId().toString()
                : null;

        String nodeHash = buildNodeHash(dto);

        var params = Values.parameters(
                "id", id.toString(),
                PARENT_ID_PARAM, parentId,
                "childrenIds", childrenIds,
                "ontologyId", ontologyId,
                "dataTypeId", dataTypeId,
                "nodeHash", nodeHash
        );

        return write(cypher, params)
                .collect().last()
                .replaceWithVoid();
    }


    /**
     * Override create() to include parent relationship in duplicate check.
     * A schema node is considered duplicate if another node exists with same properties
     * AND same parent relationship.
     * Otherwise it is NOT a duplicate, e.g. a group node like Medication can exist multiple
     * times in multiple schemas as duplicate only in properties but not in parent relationship.
     */
    @Override
    public Uni<SchemaNodeDTO> create(SchemaNodeDTO node) {
        if (node == null) {
            return Uni.createFrom().failure(new IllegalArgumentException("Node must not be null"));
        }

        boolean hasId = node.getId() != null;

        // Check for duplicate with same properties AND parent (by parent id)
        if (!hasId) {
            return existsInDBWithParent(node)
                    .flatMap(found -> {
                        if (found != null) {
                            return Uni.createFrom().item(found);
                        }
                        // Node doesn't exist yet, create it
                        return createCheckless(node);
                    });
        }

        // If an ID was provided, skip duplicate-check and create directly (upsert behavior)
        return createCheckless(node);
    }

    @Override
    public Uni<SchemaNodeDTO> createCheckless(SchemaNodeDTO node) {
        node.setLabel(getNodeLabel());
        node.setIdIfAbsent();

        String cypher = CypherTemplate.of("""
                        CREATE (n:${NODE_LABEL})
                        SET n += $props
                        RETURN n
                        """)
                .bind("NODE_LABEL", getNodeLabel())
                .build();

        Map<String, Object> props = mapper.toNodeProperties(node);
        props.put(NODE_HASH_PROPERTY, buildNodeHash(node));

        return write(cypher, Values.parameters("props", props))
                .map(mapper::fromNodeRecord)
                .collect().last();
    }

    @Override
    public Uni<SchemaNodeDTO> update(SchemaNodeDTO node) {
        if (node.getId() == null) {
            return Uni.createFrom().failure(
                    new IllegalArgumentException("DTO ID must not be null for update.")
            );
        }

        Map<String, Object> props = mapper.toNodeProperties(node);
        props.remove(idPropertyName);
        props.put(NODE_HASH_PROPERTY, buildNodeHash(node));

        String cypher = CypherTemplate.of("""
                        MATCH (n:${NODE_LABEL} {${ID_PROP}: $id})
                        SET n += $props
                        RETURN n
                        """)
                .bind("NODE_LABEL", getNodeLabel())
                .bind("ID_PROP", idPropertyName)
                .build();

        return write(cypher, Values.parameters("id", node.getId().toString(), "props", props))
                .map(mapper::fromNodeRecord)
                .collect().last();
    }

    /**
     * Check if a schema node exists with same properties AND parent relationship.
     * Must be called with no ID set!
     *
     * Handles two cases:
     * 1. Node HAS a parent: Check for existing node with matching parentId relationship
     * 2. Node has NO parent: Check for existing root-like node with no parent relationship
     */
    private Uni<SchemaNodeDTO> existsInDBWithParent(SchemaNodeDTO node) {
        if (node.getId() != null) {
            return Uni.createFrom().failure(
                    new IllegalArgumentException("Node ID must be null when checking for existence by properties.")
            );
        }

        String nodeHash = buildNodeHash(node);

        String cypher = CypherTemplate.of("""
                        MATCH (n:${SCHEMA_LABEL} {${NODE_HASH_PROP}: $nodeHash})
                        RETURN n
                        LIMIT 1
                        """)
                .bind("SCHEMA_LABEL", getNodeLabel())
                .bind("NODE_HASH_PROP", NODE_HASH_PROPERTY)
                .build();

        Value params = Values.parameters("nodeHash", nodeHash);

        return read(cypher, params)
                .map(mapper::fromNodeRecord)
                .select()
                .first()
                .toUni();
    }

    private String buildNodeHash(SchemaNodeDTO node) {
        Map<String, Object> props = mapper.toNodeProperties(node);

        props.put(PARENT_ID_PARAM, node.getParentId() != null ? node.getParentId() : ROOT_PARENT_MARKER);
        props.put(ONTOLOGY_ID_PARAM, node.getOntologyId());
        props.put(DATA_TYPE_ID_PARAM, node.getDataTypeId());

        return hashProperties(props, List.of());
    }

    @Override
    protected String getNodeLabel() {
        return SchemaNodeDTO.label(SchemaNodeDTO.class);
    }

    @Override
    protected String getEdgeLabel() {
        return SchemaEdgeDTO.label(SchemaEdgeDTO.class);
    }
}
