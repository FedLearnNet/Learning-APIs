package de.unihamburg.daibetes.api.ontology;

import bio.cosy.feddb.core.api.datamodler.datatype.DataTypeNodeDTO;
import bio.cosy.feddb.core.api.datamodler.ontology.OntologyEdgeDTO;
import bio.cosy.feddb.core.api.datamodler.ontology.OntologyNodeDTO;
import bio.cosy.feddb.core.api.datamodler.ontology.OntologyQueryAbilityDTO;
import bio.cosy.feddb.core.api.datamodler.schema.SchemaEdgeDTO;
import bio.cosy.feddb.core.api.datamodler.schema.SchemaNodeDTO;
import de.unihamburg.daibetes.api.schema.SchemaSubscriptions;
import de.unihamburg.daibetes.base.BaseDAO;
import de.unihamburg.daibetes.base.CypherTemplate;
import de.unihamburg.daibetes.helper.InterNodeEdgeLabels;
import io.quarkus.logging.Log;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.NotFoundException;
import org.neo4j.driver.Value;
import org.neo4j.driver.Values;

import java.util.*;

@ApplicationScoped
public class OntologyDAO extends BaseDAO<OntologyNodeDTO, OntologyEdgeDTO, OntologyMapper> {


    public Uni<Void> createNodeBatch(List<OntologyNodeDTO> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            return Uni.createFrom().voidItem();
        }

        List<Map<String, Object>> nodeMaps = mapper.toNodeProperties(nodes);

        String cypher = CypherTemplate.of("""
                        UNWIND $nodes AS n
                        WITH n WHERE n.cui IS NOT NULL
                        MERGE (node:${NODE_LABEL} {cui: n.cui})
                        ON CREATE SET node += n
                        ON MATCH SET
                            node.names = REDUCE(
                                acc = [],
                                x IN (coalesce(node.names, []) + coalesce(n.names, [])) |
                                CASE WHEN x IN acc THEN acc ELSE acc + x END
                            ),
                            node.codes = REDUCE(
                                acc = [],
                                x IN (coalesce(node.codes, []) + coalesce(n.codes, [])) |
                                CASE WHEN x IN acc THEN acc ELSE acc + x END
                            ),
                            node.sabs = REDUCE(
                                acc = [],
                                x IN (coalesce(node.sabs, []) + coalesce(n.sabs, [])) |
                                CASE WHEN x IN acc THEN acc ELSE acc + x END
                            ),
                            node.auis = REDUCE(
                                acc = [],
                                x IN (coalesce(node.auis, []) + coalesce(n.auis, [])) |
                                CASE WHEN x IN acc THEN acc ELSE acc + x END
                            ),
                            node.label = coalesce(node.label, n.label),
                            node.description = coalesce(node.description, n.description),
                            node.lat = coalesce(node.lat, n.lat)
                        RETURN id(node) AS neoId
                        """)
                .bind("NODE_LABEL", getNodeLabel())
                .build();

        return write(cypher, Values.parameters("nodes", nodeMaps))
                .collect().last()
                .replaceWithVoid();
    }

    public Uni<Void> createEdgeBatch(List<OntologyEdgeDTO> edges) {
        if (edges == null || edges.isEmpty()) {
            return Uni.createFrom().voidItem();
        }

        List<Map<String, Object>> edgeMaps = edges.stream()
                .map(edge -> {
                    Map<String, Object> props = mapper.toEdgeProperties(edge);
                    Map<String, Object> map = new HashMap<>();
                    map.put("srcId", edge.getSourceId());
                    map.put("dstId", edge.getTargetId());
                    map.put("props", props);
                    return map;
                })
                .toList();

        String cypher = CypherTemplate.of("""
                        UNWIND $edges AS e
                        MATCH (s:${NODE_LABEL} {cui: e.srcId})
                        MATCH (t:${NODE_LABEL} {cui: e.dstId})
                        WITH e, s, t, e.props AS p
                        MERGE (s)-[r:${EDGE_LABEL} {
                            rel: p.rel,
                            rela: p.rela,
                            sab: p.sab
                        }]->(t)
                        ON CREATE SET r += p
                        ON MATCH  SET r += p
                        """)
                .bind("NODE_LABEL", getNodeLabel())
                .bind("EDGE_LABEL", getEdgeLabel())
                .build();

        //explicitly specify Record type to avoid type erasure issues
        return write(cypher, Values.parameters("edges", edgeMaps))
                .collect().last()
                .replaceWithVoid();
    }

    public Uni<Boolean> existsByCodeId(String code) {

        String cypher = CypherTemplate.of("""
                        MATCH (n:${LABEL})
                        WHERE $code IN n.codes
                        RETURN COUNT(n) AS count
                        """)
                .bind("LABEL", getNodeLabel())
                .build();
        //explicitly specify Record type to avoid type erasure issues
        return read(cypher, Values.parameters("code", code))
                .collect().last()
                .onItem().transform(record -> {
                    if (record == null) {
                        return false;
                    }
                    long count = record.get("count").asLong(0);
                    return count > 0;
                });
    }

    public Uni<OntologyNodeDTO> findByCUI(String id) {
        String cypher = CypherTemplate.of("""
                        MATCH (n:${NODE_LABEL} {${ID_PROP}: $id})
                        RETURN n
                        LIMIT 1
                        """)
                .bind("NODE_LABEL", getNodeLabel())
                .bind("ID_PROP", "cui")
                .build();

        return read(cypher, Values.parameters("id", id.toString()))
                .map(mapper::fromNodeRecord)
                .select()
                .first()
                .toUni()
                .onItem().ifNull().failWith(() -> new NotFoundException("Node not found"));

    }

    public Uni<List<OntologyNodeDTO>> getByIds(List<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return Uni.createFrom().item(Collections.emptyList());
        }

        List<String> normalizedIds = ids.stream()
                .filter(Objects::nonNull)
                .map(UUID::toString)
                .distinct()
                .toList();

        if (normalizedIds.isEmpty()) {
            return Uni.createFrom().item(Collections.emptyList());
        }

        String cypher = CypherTemplate.of("""
                        MATCH (n:${NODE_LABEL})
                        WHERE n.${ID_PROP} IN $ids
                        RETURN n
                        """)
                .bind("NODE_LABEL", getNodeLabel())
                .bind("ID_PROP", idPropertyName)
                .build();

        return read(cypher, Values.parameters("ids", normalizedIds))
                .map(mapper::fromNodeRecord)
                .collect().asList();
    }


    private String getSearchCypherWhere() {
        return """
                (n.names IS NOT NULL AND ANY(name IN n.names
                                WHERE toLower(name) CONTAINS toLower($search)))
                            OR
                
                            (n.description IS NOT NULL AND toLower(n.description)
                                CONTAINS toLower($search))
                            OR
                
                            (n.codes IS NOT NULL AND ANY(code IN n.codes
                                WHERE toLower(code) CONTAINS toLower($search)))
                            OR
                
                            (n.sabs IS NOT NULL AND ANY(sab IN n.sabs
                                WHERE toLower(sab) CONTAINS toLower($search)))
                            OR
                
                            (n.cui IS NOT NULL AND toLower(n.cui)
                                CONTAINS toLower($search))
                            OR
                
                            (n.auis IS NOT NULL AND ANY(aui IN n.auis
                                WHERE toLower(aui) CONTAINS toLower($search)))
                            OR
                
                            (n.lat IS NOT NULL AND toLower(n.lat)
                                CONTAINS toLower($search))
                """;
    }

    public Multi<OntologyNodeDTO> findAll(String search, int page, int pageSize) {
        String normalizedSearch = search == null ? "" : search.trim();

        String cypher = CypherTemplate.of("""
                        MATCH (n:${NODE_LABEL})
                        WHERE
                           ${WHERE}
                        RETURN n
                        ORDER BY n.${ID_PROP}
                        SKIP $skip
                        LIMIT $limit
                        """)
                .bind("NODE_LABEL", getNodeLabel())
                .bind("WHERE", getSearchCypherWhere())
                .bind("ID_PROP", idPropertyName)
                .build();

        return read(cypher, Values.parameters(
                "skip", page * pageSize,
                "limit", pageSize,
                "search", normalizedSearch))
                .map(mapper::fromNodeRecord);
    }

    public Uni<Long> countAll(String search) {
        String normalizedSearch = search == null ? "" : search.trim();

        String cypher = CypherTemplate.of("""
                        MATCH (n:${NODE_LABEL})
                        WHERE
                           ${WHERE}
                        RETURN count(n) AS total
                        """)
                .bind("NODE_LABEL", getNodeLabel())
                .bind("WHERE", getSearchCypherWhere())
                .bind("ID_PROP", idPropertyName)
                .build();

        return read(cypher, Values.parameters(
                "search", normalizedSearch))
                .map(r -> r.get("total").asLong())
                .toUni();
    }

    /**
     * Checks if any ontology nodes exist in the database.
     * Used for startup checks to determine if auto-import is needed.
     *
     * @return Uni emitting true if at least one ontology node exists, false otherwise
     */
    public Uni<Boolean> hasNodes() {
        String cypher = CypherTemplate.of("""
                        MATCH (n:${NODE_LABEL})
                        RETURN n LIMIT 1
                        """)
                .bind("NODE_LABEL", getNodeLabel())
                .build();

        return read(cypher, Values.parameters())
                .toUni()
                .map(r -> r != null)
                .onFailure().recoverWithItem(false);
    }


    public Uni<OntologyNodeDTO> searchById(String search) {
        String normalizedSearch = search == null ? "" : search.trim();

        String cypher = CypherTemplate.of("""
                        MATCH (n:${NODE_LABEL})
                        WHERE
                            (n.codes IS NOT NULL AND ANY(code IN n.codes
                                WHERE toLower(code) = toLower($search)))
                            OR
                        
                            (n.cui IS NOT NULL AND toLower(n.cui) = toLower($search))
                            OR
                        
                            (n.auis IS NOT NULL AND ANY(aui IN n.auis
                                WHERE toLower(aui) = toLower($search)))
                        RETURN n
                        LIMIT 1
                        """)
                .bind("NODE_LABEL", getNodeLabel())
                .bind("ID_PROP", idPropertyName)
                .build();

        return read(cypher, Values.parameters(
                "search", normalizedSearch))
                .map(mapper::fromNodeRecord).select().last().toUni();
    }

    public Multi<OntologyNodeDTO> getChildren(UUID id) {
        String cypher = CypherTemplate.of("""
                        MATCH (n:${NODE_LABEL} {cui: $id})-[:${EDGE_LABEL}]->(child:${NODE_LABEL})
                        RETURN child AS node
                        """)
                .bind("NODE_LABEL", getNodeLabel())
                .bind("EDGE_LABEL", getEdgeLabel())
                .build();

        return read(cypher, Values.parameters("id", id.toString()))
                .onItem().transform(mapper::fromNodeRecord);
    }


    public Multi<OntologyQueryAbilityDTO> listQueryability() {
        String cypher = CypherTemplate.of("""
                        MATCH (r:RootSchemaNode)
                        WHERE size(r.subscriptions) > 0
                        WITH r, r.subscriptions AS subscriptions
                        MATCH path = (r)-[*]->(o:${LABEL})
                        MATCH (o)-[*]->(d:DataNode)
                        RETURN DISTINCT
                            o.unique_id AS ontologyId,
                            d.unique_id AS dataTypeId,
                            subscriptions AS clients
                        """)
                .bind("LABEL", getNodeLabel())
                .build();

        return read(cypher)
                .onItem().transform(mapper::toQueryAbility);
    }


    public Multi<SchemaSubscriptions> getAllRelatedSchema(List<UUID> ontologyIds) {
        if (ontologyIds == null || ontologyIds.isEmpty()) {
            return Multi.createFrom().empty();
        }

        List<String> uniqueIds = ontologyIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .map(UUID::toString)
                .toList();

        String cypher = CypherTemplate.of("""
                        MATCH (o:${ONTO_LABEL})
                        WHERE o.${ID_PROP} IN $ontology_ids
                        
                        MATCH (s:${SCHEMA_LABEL})-[:${RELATES_TO_ONTOLOGY_REL}]->(o)
                        WITH s
                        
                        MATCH path = (s)<-[:${SUB_SCHEMA_REL}*0..]-(root:${ROOT_SCHEMA_LABEL})
                        WHERE root.isRoot = true
                        WITH root
                        
                        MATCH (root)-[:${SUB_SCHEMA_REL}*0..]->(child:${SCHEMA_LABEL})
                        
                        RETURN DISTINCT
                            child.${ID_PROP}        AS schemaId,
                            root.subscriptions      AS subscriptions
                        """)
                .bind("ONTO_LABEL", OntologyNodeDTO.nodeLabel())
                .bind("SCHEMA_LABEL", SchemaNodeDTO.nodeLabel())
                .bind("ROOT_SCHEMA_LABEL", SchemaNodeDTO.nodeLabel()) // if you have such DTO
                .bind("ID_PROP", idPropertyName)
                .bind("RELATES_TO_ONTOLOGY_REL", InterNodeEdgeLabels.SCHEMA_TO_ONTOLOGY)
                .bind("SUB_SCHEMA_REL", SchemaEdgeDTO.edgeLabel())
                .build();

        return read(cypher, Values.parameters("ontology_ids", uniqueIds))
                .map(record -> {
                    String schemaId = record.get("schemaId").asString(null);

                    List<String> subs = record.get("subscriptions").isNull()
                            ? Collections.emptyList()
                            : record.get("subscriptions").asList(Value::asString);

                    return new SchemaSubscriptions(schemaId, subs);
                });
    }

    public Uni<Void> addOntologyToDataTypesEdges(UUID ontologyId, List<UUID> dataTypeIds) {
        if (ontologyId == null) {
            Log.warnf("Attempted to add edges with null ontologyId");
            return Uni.createFrom().voidItem();
        }

        List<String> dtIds = dataTypeIds == null
                ? List.of()
                : dataTypeIds.stream()
                  .filter(Objects::nonNull)
                  .map(UUID::toString)
                  .distinct()
                  .toList();

        if (dtIds.isEmpty()) {
            return Uni.createFrom().voidItem();
        }

        String cypher = CypherTemplate.of("""
                        MATCH (o:${ONTO_LABEL} {${ID_PROP}: $ontologyId})
                        UNWIND $dataTypeIds AS dtid
                        MATCH (d:${DATA_LABEL} {${ID_PROP}: dtid})
                        MERGE (o)-[:${ONTO_HAS_DATA_TYPE_REL}]->(d)
                        RETURN count(d) AS updated
                        """)
                .bind("ONTO_LABEL", OntologyNodeDTO.nodeLabel())
                .bind("DATA_LABEL", DataTypeNodeDTO.nodeLabel())
                .bind("ID_PROP", idPropertyName)
                .bind("ONTO_HAS_DATA_TYPE_REL", InterNodeEdgeLabels.DATA_TYPE_TO_ONTOLOGY)
                .build();

        return write(cypher, Values.parameters(
                "ontologyId", ontologyId.toString(),
                "dataTypeIds", dtIds
        ))
                .collect().last()
                .replaceWithVoid();
    }

    @Override
    protected String getNodeLabel() {
        return OntologyNodeDTO.label(OntologyNodeDTO.class);
    }

    @Override
    protected String getEdgeLabel() {
        return OntologyEdgeDTO.label(OntologyEdgeDTO.class);
    }
}
