package de.unihamburg.daibetes.base;

import bio.cosy.feddb.core.api.datamodler.BaseEdgeDTO;
import bio.cosy.feddb.core.api.datamodler.BaseNodeDTO;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Record;
import org.neo4j.driver.Value;
import org.neo4j.driver.Values;
import org.neo4j.driver.reactive.ReactiveResult;
import org.neo4j.driver.reactive.ReactiveSession;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Generic base DAO for Neo4j using DTOs
 * Provides reactive CRUD with Neo4j reactive driver and Mutiny.
 */
public abstract class BaseDAO<NODE extends BaseNodeDTO, EDGE extends BaseEdgeDTO, MAPPER extends BaseMapper<NODE, EDGE>> {

    @Inject
    Driver driver;

    @Inject
    public MAPPER mapper;

    @ConfigProperty(name = "feddb.neo4j.id-property", defaultValue = "id")
    protected String idPropertyName;

    protected abstract String getNodeLabel();

    protected abstract String getEdgeLabel();

    protected static final String LABEL_PROPERTY = "label";
    protected static final String CREATED_AT_PROPERTY = "createdAt";
    protected static final String UPDATED_AT_PROPERTY = "updatedAt";
    protected static final String VERSION_PROPERTY = "version";
    protected static final String SUBSCRIPTIONS_PROPERTY = "subscriptions";
    protected static final String CREATED_BY_PROPERTY = "createdBy";
    protected static final String NODE_HASH_PROPERTY = "nodeHash";

    protected Uni<Void> closeSession(ReactiveSession session) {
        return Uni.createFrom().publisher(session.close()).replaceWithVoid();
    }

    protected ReactiveSession openSession() {
        return driver.session(ReactiveSession.class);
    }

    /**
     * Computes a deterministic SHA-256 hash from the provided properties.
     *
     * The method hashes all provided properties "as-is" except technical/default fields that are ignored
     * automatically (id, label, createdAt, updatedAt, version, subscriptions, createdBy, nodeHash).
     * Additional fields can be ignored via {@code additionalIgnoredProperties}. This allows callers to pass
     * broader property maps while explicitly excluding unwanted keys from hash identity.
     *
     * Connected DTO objects are canonicalized by ID only (for {@link BaseNodeDTO} and {@link BaseEdgeDTO}).
     */
    protected String hashProperties(Map<String, Object> properties, Collection<String> additionalIgnoredProperties) {
        if (properties == null || properties.isEmpty()) {
            return sha256("");
        }

        Set<String> ignoredProperties = new HashSet<>(Set.of(
                idPropertyName,
                LABEL_PROPERTY,
                CREATED_AT_PROPERTY,
                UPDATED_AT_PROPERTY,
                VERSION_PROPERTY,
                SUBSCRIPTIONS_PROPERTY,
                CREATED_BY_PROPERTY,
                NODE_HASH_PROPERTY
        ));

        if (additionalIgnoredProperties != null) {
            ignoredProperties.addAll(additionalIgnoredProperties);
        }

        TreeMap<String, Object> sorted = new TreeMap<>();
        for (Map.Entry<String, Object> entry : properties.entrySet()) {
            if (entry.getKey() == null || ignoredProperties.contains(entry.getKey())) {
                continue;
            }
            sorted.put(entry.getKey(), entry.getValue());
        }

        StringBuilder canonical = new StringBuilder();
        for (Map.Entry<String, Object> entry : sorted.entrySet()) {
            canonical.append(entry.getKey())
                    .append('=')
                    .append(canonicalize(entry.getValue()))
                    .append(';');
        }

        return sha256(canonical.toString());
    }

    private String canonicalize(Object value) {
        if (value == null) {
            return "null";
        }

        return String.valueOf(value);
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }


    public Multi<NODE> findAll() {
        String cypher = CypherTemplate.of("""
                        MATCH (n:${NODE_LABEL})
                        RETURN n
                        ORDER BY n.${ID_PROP}
                        """)
                .bind("NODE_LABEL", getNodeLabel())
                .bind("ID_PROP", idPropertyName)
                .build();

        return read(cypher)
                .map(mapper::fromNodeRecord);
    }

    public Uni<Long> countAll() {
        String cypher = CypherTemplate.of("""
                        MATCH (n:${NODE_LABEL})
                        RETURN count(n) AS total
                        """)
                .bind("NODE_LABEL", getNodeLabel())
                .build();

        return read(cypher)
                .map(record -> record.get("total").asLong())
                .toUni();
    }

    public Multi<EDGE> findAllEdges(UUID nodeId) {
        String cypher = CypherTemplate.of("""
                        MATCH (n:${NODE_LABEL} { ${ID_PROP}: $id })-[r:${REL_LABEL}]-(m:${NODE_LABEL})
                        RETURN r, n.${ID_PROP} AS sourceId, m.${ID_PROP} AS targetId
                        """)
                .bind("NODE_LABEL", getNodeLabel())
                .bind("ID_PROP", idPropertyName)
                .bind("REL_LABEL", getEdgeLabel())
                .build();

        return read(cypher, Values.parameters("id", nodeId.toString()))
                .map(mapper::fromEdgeRecord);
    }

    public Multi<NODE> findAll(int page, int pageSize) {
        String cypher = CypherTemplate.of("""
                        MATCH (n:${NODE_LABEL})
                        RETURN n
                        ORDER BY n.${ID_PROP}
                        SKIP $skip
                        LIMIT $limit
                        """)
                .bind("NODE_LABEL", getNodeLabel())
                .bind("ID_PROP", idPropertyName)
                .build();

        return read(cypher, Values.parameters(
                "skip", page * pageSize,
                "limit", pageSize
        ))
                .map(mapper::fromNodeRecord);
    }


    public Uni<EDGE> createEdge(String fromId, String toId, String relType, Map<String, Object> props) {
        String cypher = CypherTemplate.of("""
                        MATCH (a:${NODE_LABEL} {${ID_PROP}: $fromId}),
                              (b:${NODE_LABEL} {${ID_PROP}: $toId})
                        MERGE (a)-[r:${REL_TYPE}]->(b)
                        ${SET_PROPS}
                        RETURN r
                        """)
                .bind("NODE_LABEL", getNodeLabel())
                .bind("ID_PROP", idPropertyName)
                .bind("REL_TYPE", relType)
                .bind("SET_PROPS", (props == null || props.isEmpty()) ? "" : "SET r += $props")
                .build();
        Value params = (props == null || props.isEmpty())
                ? Values.parameters("fromId", fromId, "toId", toId)
                : Values.parameters("fromId", fromId, "toId", toId, "props", props);

        return write(cypher, params)
                .onItem().transform(mapper::fromEdgeRecord)
                .collect().last();
    }

    public Uni<EDGE> createEdge(EDGE edge) {
        return createEdge(edge.getSourceId(), edge.getTargetId(), this.getEdgeLabel(), mapper.toEdgeProperties(edge));
    }


    public Uni<NODE> findById(UUID id) {
        String cypher = CypherTemplate.of("""
                        MATCH (n:${NODE_LABEL} {${ID_PROP}: $id})
                        RETURN n
                        LIMIT 1
                        """)
                .bind("NODE_LABEL", getNodeLabel())
                .bind("ID_PROP", idPropertyName)
                .build();

        return read(cypher, Values.parameters("id", id.toString()))
                .map(mapper::fromNodeRecord)
                .select()
                .first()
                .toUni()
                .onItem().ifNull().failWith(() -> new NotFoundException("Node not found"));

    }

    public Uni<NODE> create(NODE node) {
        if (node == null) {
            return Uni.createFrom().failure(new IllegalArgumentException("Node must not be null"));
        }

        boolean hasId = node.getId() != null;
        // check for duplicate with the same properties
        if (!hasId) {
            return existsInDB(node)
                    .flatMap(found -> {
                        if (found != null) {
                            return Uni.createFrom().item(found);
                        }
                        // Node doesnt exist yet, create it
                        return createCheckless(node);
                    });
        }

        // If an ID was provided, skip duplicate-check and create directly (upsert behavior)
        return createOrUpdate(node);
    }

    public Uni<NODE> createOrUpdate(NODE node) {
        node.setLabel(getNodeLabel());
        if (node.getId() == null) {
            return createCheckless(node);
        }

        String cypher = CypherTemplate.of("""
            MERGE (n:${NODE_LABEL} {${ID_PROP}: $id})
            SET n += $props
            RETURN n
            """)
                .bind("NODE_LABEL", getNodeLabel())
                .bind("ID_PROP", idPropertyName)
                .build();

        Map<String, Object> props = mapper.toNodeProperties(node);

        return write(cypher, Values.parameters(
                "id", node.getId().toString(),
                "props", props
        ))
                .map(mapper::fromNodeRecord)
                .collect().last();
    }

    public Uni<NODE> createCheckless(NODE node) {
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

        return write(cypher, Values.parameters("props", props))
                .map(mapper::fromNodeRecord)
                .collect().last();
    }

    private Uni<NODE> existsInDB(NODE node) {
        // Must be called with no ID set!
        if (node.getId() != null) {
            return Uni.createFrom().failure(
                    new IllegalArgumentException("Node ID must be null when checking for existence by properties.")
            );
        }

        // Build property map for check and remove id property
        Map<String, Object> props = mapper.toNodeProperties(node);
        if (props == null || props.isEmpty()) {
            return Uni.createFrom().nullItem();
        }
        props.remove(idPropertyName);
        if (props.isEmpty()) {
            return Uni.createFrom().nullItem();
        }

        // Build WHERE clause: n.`key` = $key AND ...
        // Also flatten map into alternating key-value arguments for Values.parameters()
        StringBuilder where = new StringBuilder();
        java.util.List<Object> paramList = new java.util.ArrayList<>();
        int i = 0;
        for (Map.Entry<String, Object> entry : props.entrySet()) {
            if (i > 0) where.append(" AND ");
            // AND connect to the previous property, skipping this at the first iteration
            String key = entry.getKey();
            where.append("n.`").append(key).append("` = $").append(key);
            paramList.add(key);
            paramList.add(entry.getValue());
            i++;
        }

        String cypher = CypherTemplate.of("""
                        MATCH (n:${NODE_LABEL})
                        WHERE ${WHERE}
                        RETURN n
                        LIMIT 1
                        """)
                .bind("NODE_LABEL", getNodeLabel())
                .bind("WHERE", where.toString())
                .build();

        Value params = Values.parameters(paramList.toArray());

        return read(cypher, params)
                .map(mapper::fromNodeRecord)
                .select()
                .first()
                .toUni();
    }

    public Uni<NODE> update(NODE NODE) {
        if (NODE.getId() == null) {
            return Uni.createFrom().failure(
                    new IllegalArgumentException("DTO ID must not be null for update.")
            );
        }

        Map<String, Object> props = mapper.toNodeProperties(NODE);
        props.remove(idPropertyName);

        String cypher = CypherTemplate.of("""
                        MATCH (n:${NODE_LABEL} {${ID_PROP}: $id})
                        SET n += $props
                        RETURN n
                        """)
                .bind("NODE_LABEL", getNodeLabel())
                .bind("ID_PROP", idPropertyName)
                .build();

        return write(cypher, Values.parameters("id", NODE.getId().toString(), "props", props))
                .map(mapper::fromNodeRecord)
                .collect().last();
    }


    public Uni<Void> deleteById(UUID id) {
        String cypher = CypherTemplate.of("""
                        MATCH (n:${NODE_LABEL} {${ID_PROP}: $id})
                        DETACH DELETE n
                        """)
                .bind("NODE_LABEL", getNodeLabel())
                .bind("ID_PROP", idPropertyName)
                .build();

        return Multi.createFrom().resource(
                        this::openSession,
                        session -> session.executeWrite(tx -> {
                            var result = tx.run(cypher, Values.parameters("id", id.toString()));

                            return Multi.createFrom().publisher(result)
                                    .flatMap(ReactiveResult::consume);
                        })
                )
                .withFinalizer((ReactiveSession session) -> this.closeSession(session))
                .onItem().ignore().toUni();
    }

    public Multi<Record> read(String cypher, Value values) {
        return Multi.createFrom().<ReactiveSession, Record>resource(
                        this::openSession,
                        session ->
                                session.executeRead(tx -> {
                                    var result = tx.run(cypher, values);
                                    return Multi.createFrom().publisher(result).flatMap(ReactiveResult::records);
                                })
                )
                .withFinalizer((ReactiveSession session) -> this.closeSession(session));
    }

    public Multi<Record> read(String cypher) {
        return Multi.createFrom().<ReactiveSession, Record>resource(
                        this::openSession,
                        session ->
                                session.executeRead(tx -> {
                                    var result = tx.run(cypher);
                                    return Multi.createFrom().publisher(result).flatMap(ReactiveResult::records);
                                })
                )
                .withFinalizer((ReactiveSession session) -> this.closeSession(session));
    }

    public Multi<Record> write(String cypher, Value values) {
        return Multi.createFrom().<ReactiveSession, Record>resource(
                        this::openSession,
                        session ->
                                session.executeWrite(tx -> {
                                    var result = tx.run(cypher, values);
                                    return Multi.createFrom().publisher(result).flatMap(ReactiveResult::records);
                                })
                )
                .withFinalizer((ReactiveSession session) -> this.closeSession(session));
    }
}
