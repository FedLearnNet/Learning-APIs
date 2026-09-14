package de.unihamburg.daibetes.api.datatype;

import bio.cosy.feddb.core.api.datamodler.datatype.DataTypeEdgeDTO;
import bio.cosy.feddb.core.api.datamodler.datatype.DataTypeNodeDTO;
import bio.cosy.feddb.core.api.datamodler.datatype.DataTypeNodeDetailDTO;
import bio.cosy.feddb.core.api.datamodler.datatype.DataTypeSchemaEdgeDTO;
import bio.cosy.feddb.core.api.datamodler.ontology.OntologyNodeDTO;
import bio.cosy.feddb.core.api.datamodler.schema.SchemaNodeDTO;
import bio.cosy.feddb.core.base.PagedResponse;
import de.unihamburg.daibetes.base.BaseDAO;
import de.unihamburg.daibetes.base.CypherTemplate;
import de.unihamburg.daibetes.helper.InterNodeEdgeLabels;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.BadRequestException;
import org.neo4j.driver.Values;

import java.util.*;

@ApplicationScoped
public class DataTypeDAO extends BaseDAO<DataTypeNodeDTO, DataTypeEdgeDTO, DataTypeMapper> {

    public Multi<DataTypeNodeDetailDTO> getAll(int page, int pageSize, String search) {
        int safePage = Math.max(0, page);
        int safePageSize = Math.max(1, pageSize);
        String q = search == null ? "" : search.trim().toLowerCase();

        String cypher = CypherTemplate.of("""
                        MATCH (d:${DATA_LABEL})
                        
                        WHERE $q = ''
                        OR toLower(coalesce(d.name, '')) CONTAINS $q
                        OR toLower(coalesce(d.description, '')) CONTAINS $q
                        OR toLower(coalesce(toString(d.type), '')) CONTAINS $q
                        OR any(v IN coalesce(d.allowedValues, []) WHERE toLower(coalesce(v, '')) CONTAINS $q)
                        OR toLower(coalesce(toString(d.validations), '')) CONTAINS $q
                        
                        OPTIONAL MATCH (d)<-[:${SCHEMA_HAS_DATA_TYPE_REL}]-(s:${SCHEMA_LABEL})
                        OPTIONAL MATCH (d)<-[:${ONTO_HAS_DATA_TYPE_REL}]-(o:${ONTO_LABEL})
                        
                        RETURN
                            d AS node,
                            collect(DISTINCT s.${ID_PROP}) AS schemaIds,
                            collect(DISTINCT o)           AS ontologies
                        SKIP $skip
                        LIMIT $limit
                        """)
                .bind("DATA_LABEL", getNodeLabel())
                .bind("SCHEMA_LABEL", SchemaNodeDTO.nodeLabel())
                .bind("ONTO_LABEL", OntologyNodeDTO.nodeLabel())
                .bind("ID_PROP", idPropertyName)
                .bind("SCHEMA_HAS_DATA_TYPE_REL", InterNodeEdgeLabels.SCHEMA_TO_ONTOLOGY)
                .bind("ONTO_HAS_DATA_TYPE_REL", InterNodeEdgeLabels.DATA_TYPE_TO_ONTOLOGY)
                .build();

        return read(cypher,
                Values.parameters(
                        "q", q,
                        "skip", safePage * safePageSize,
                        "limit", safePageSize
                ))
                .map(mapper::fromDetailRecord);
    }
    public Uni<PagedResponse<DataTypeNodeDetailDTO>> getAllPaged(int page, int pageSize, String search) {
        int safePage = Math.max(0, page);
        int safePageSize = Math.max(1, pageSize);
        int skip = safePage * safePageSize;
        String q = search == null ? "" : search.trim().toLowerCase();

        String cypher = CypherTemplate.of("""
            MATCH (d:${DATA_LABEL})
            WHERE $q = ''
               OR toLower(coalesce(d.name, '')) CONTAINS $q
               OR toLower(coalesce(d.description, '')) CONTAINS $q
               OR toLower(coalesce(toString(d.type), '')) CONTAINS $q
               OR any(v IN coalesce(d.allowedValues, []) WHERE toLower(coalesce(v, '')) CONTAINS $q)
               OR toLower(coalesce(toString(d.validations), '')) CONTAINS $q
            WITH count(DISTINCT d) AS total

            MATCH (d:${DATA_LABEL})
            WHERE $q = ''
               OR toLower(coalesce(d.name, '')) CONTAINS $q
               OR toLower(coalesce(d.description, '')) CONTAINS $q
               OR toLower(coalesce(toString(d.type), '')) CONTAINS $q
               OR any(v IN coalesce(d.allowedValues, []) WHERE toLower(coalesce(v, '')) CONTAINS $q)
               OR toLower(coalesce(toString(d.validations), '')) CONTAINS $q
            WITH DISTINCT d, total
            SKIP $skip
            LIMIT $limit

            OPTIONAL MATCH (d)<-[:${SCHEMA_HAS_DATA_TYPE_REL}]-(s:${SCHEMA_LABEL})
            OPTIONAL MATCH (d)<-[:${ONTO_HAS_DATA_TYPE_REL}]-(o:${ONTO_LABEL})

            RETURN
                d AS node,
                collect(DISTINCT s.${ID_PROP}) AS schemaIds,
                collect(DISTINCT o)           AS ontologies,
                total                         AS total
            """)
                .bind("DATA_LABEL", getNodeLabel())
                .bind("SCHEMA_LABEL", SchemaNodeDTO.nodeLabel())
                .bind("ONTO_LABEL", OntologyNodeDTO.nodeLabel())
                .bind("ID_PROP", idPropertyName)
                .bind("SCHEMA_HAS_DATA_TYPE_REL", InterNodeEdgeLabels.SCHEMA_TO_ONTOLOGY)
                .bind("ONTO_HAS_DATA_TYPE_REL", InterNodeEdgeLabels.DATA_TYPE_TO_ONTOLOGY)
                .build();

        return read(cypher, Values.parameters(
                "q", q,
                "skip", skip,
                "limit", safePageSize
        ))
                .map(r -> new AbstractMap.SimpleEntry<>(
                        mapper.fromDetailRecord(r),
                        r.get("total").asLong(0L)
                ))
                .collect().asList()
                .onItem().transform(entries -> {
                    long total = entries.isEmpty() ? 0L : entries.get(0).getValue();
                    var items = entries.stream().map(Map.Entry::getKey).toList();
                    return new PagedResponse<>(items, safePage, safePageSize, total);
                });
    }
    public Multi<DataTypeNodeDetailDTO> getAllByOntology(UUID ontologyId, String search) {
        String q = search == null ? "" : search.trim().toLowerCase();

        String cypher = CypherTemplate.of("""
                        MATCH (o:${ONTO_LABEL} {${ID_PROP}: $ontology_id})
                              <-[:${HAS_DATA_TYPE_REL}]-(d:${DATA_LABEL})
                        
                        WHERE $q = ''
                        OR toLower(coalesce(d.name, '')) CONTAINS $q
                        OR toLower(coalesce(d.description, '')) CONTAINS $q
                        OR toLower(coalesce(toString(d.type), '')) CONTAINS $q
                        OR any(v IN coalesce(d.allowedValues, []) WHERE toLower(coalesce(v, '')) CONTAINS $q)
                        OR toLower(coalesce(toString(d.validations), '')) CONTAINS $q
                        
                        OPTIONAL MATCH (d)<-[:${SCHEMA_HAS_DATA_TYPE_REL}]-(s:${SCHEMA_LABEL})
                        OPTIONAL MATCH (d)<-[:${ONTO_HAS_DATA_TYPE_REL}]-(oAll:${ONTO_LABEL})
                        
                        RETURN
                            d AS node,
                            collect(DISTINCT s.${ID_PROP})    AS schemaIds,
                            collect(DISTINCT oAll)            AS ontologies
                        """)
                .bind("ONTO_LABEL", OntologyNodeDTO.nodeLabel())
                .bind("SCHEMA_LABEL", SchemaNodeDTO.nodeLabel())
                .bind("ID_PROP", idPropertyName)
                .bind("HAS_DATA_TYPE_REL", InterNodeEdgeLabels.DATA_TYPE_TO_ONTOLOGY)
                .bind("SCHEMA_HAS_DATA_TYPE_REL", InterNodeEdgeLabels.SCHEMA_TO_ONTOLOGY)
                .bind("ONTO_HAS_DATA_TYPE_REL", InterNodeEdgeLabels.DATA_TYPE_TO_ONTOLOGY)
                .bind("DATA_LABEL", DataTypeNodeDTO.nodeLabel())
                .build();

        return read(cypher, Values.parameters("ontology_id", ontologyId.toString(), "q", q))
                .map(mapper::fromDetailRecord);
    }

    public Uni<PagedResponse<DataTypeNodeDetailDTO>> getAllBySchema(UUID schemaId, int page, int pageSize) {
        int skip = Math.max(0, page) * Math.max(1, pageSize);

        String cypher = CypherTemplate.of("""
                        MATCH (s:${SCHEMA_LABEL} {${ID_PROP}: $schema_id})-[:${HAS_DATA_TYPE_REL}]->(d:${DATA_LABEL})
                        WITH count(DISTINCT d) AS total
                        
                        MATCH (s:${SCHEMA_LABEL} {${ID_PROP}: $schema_id})-[:${HAS_DATA_TYPE_REL}]->(d:${DATA_LABEL})
                        WITH DISTINCT d, total
                        SKIP $skip
                        LIMIT $limit
                        
                        OPTIONAL MATCH (d)<-[:${HAS_DATA_TYPE_REL}]-(allSchemas:${SCHEMA_LABEL})
                        OPTIONAL MATCH (d)-[:${ONTOLOGY_TO_DATATYPE_REL}]->(o:${ONTOLOGY_LABEL})
                        
                        RETURN
                            d AS node,
                            collect(DISTINCT allSchemas.${ID_PROP}) AS schemaIds,
                            collect(DISTINCT o)                    AS ontologies,
                            total                                  AS total
                        """)
                .bind("SCHEMA_LABEL", SchemaNodeDTO.nodeLabel())
                .bind("ID_PROP", idPropertyName)
                .bind("HAS_DATA_TYPE_REL", InterNodeEdgeLabels.SCHEMA_TO_DATA_TYPE)
                .bind("DATA_LABEL", DataTypeNodeDTO.nodeLabel())
                .bind("ONTOLOGY_TO_DATATYPE_REL", InterNodeEdgeLabels.DATA_TYPE_TO_ONTOLOGY)
                .bind("ONTOLOGY_LABEL", OntologyNodeDTO.nodeLabel())
                .build();

        return read(cypher, Values.parameters(
                "schema_id", schemaId.toString(),
                "skip", skip,
                "limit", pageSize
        ))
                .map(r -> new AbstractMap.SimpleEntry<>(
                        mapper.fromDetailRecord(r),
                        r.get("total").asLong(0L)
                ))
                .collect().asList()
                .onItem().transform(entries -> {
                    long total = entries.isEmpty() ? 0L : entries.getFirst().getValue();
                    List<DataTypeNodeDetailDTO> items = entries.stream().map(Map.Entry::getKey).toList();
                    return new PagedResponse<>(items, page, pageSize, total);
                });
    }

    public Multi<DataTypeNodeDetailDTO> getAllFiltered(List<UUID> dataTypeIds) {
        if (dataTypeIds == null || dataTypeIds.isEmpty()) {
            return Multi.createFrom().empty();
        }

        List<String> ids = dataTypeIds.stream()
                .filter(Objects::nonNull)
                .map(UUID::toString)
                .toList();

        String cypher = CypherTemplate.of("""
                        MATCH (d:${DATA_LABEL})
                        WHERE d.${ID_PROP} IN $ids
                        
                        OPTIONAL MATCH (d)<-[:${SCHEMA_HAS_DATA_TYPE_REL}]-(s:${SCHEMA_LABEL})
                        
                        OPTIONAL MATCH (d)<-[:${ONTO_HAS_DATA_TYPE_REL}]-(o:${ONTO_LABEL})
                        
                        RETURN
                            d AS node,
                            collect(DISTINCT s.${ID_PROP}) AS schemaIds,
                            collect(DISTINCT o)           AS ontologies
                        """)
                .bind("DATA_LABEL", getNodeLabel())
                .bind("SCHEMA_LABEL", SchemaNodeDTO.nodeLabel())
                .bind("ONTO_LABEL", OntologyNodeDTO.nodeLabel())
                .bind("ID_PROP", idPropertyName)
                .bind("SCHEMA_HAS_DATA_TYPE_REL", InterNodeEdgeLabels.SCHEMA_TO_ONTOLOGY)
                .bind("ONTO_HAS_DATA_TYPE_REL", InterNodeEdgeLabels.DATA_TYPE_TO_ONTOLOGY)
                .build();

        return read(cypher, Values.parameters("ids", ids))
                .map(mapper::fromDetailRecord);
    }

    public Uni<List<DataTypeNodeDTO>> getByIds(List<UUID> ids) {
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

    public Uni<PagedResponse<DataTypeNodeDetailDTO>> getAllFilteredPaged(
            List<UUID> dataTypeIds,
            int page,
            int pageSize
    ) {
        if (dataTypeIds == null || dataTypeIds.isEmpty()) {
            return Uni.createFrom().item(new PagedResponse<>(List.of(), page, pageSize, 0));
        }

        int skip = Math.max(0, page) * Math.max(1, pageSize);

        List<String> ids = dataTypeIds.stream()
                .filter(Objects::nonNull)
                .map(UUID::toString)
                .toList();

        String cypher = CypherTemplate.of("""
            MATCH (d:${DATA_LABEL})
            WHERE d.${ID_PROP} IN $ids
            WITH count(DISTINCT d) AS total

            MATCH (d:${DATA_LABEL})
            WHERE d.${ID_PROP} IN $ids
            WITH DISTINCT d, total
            SKIP $skip
            LIMIT $limit

            OPTIONAL MATCH (d)<-[:${SCHEMA_HAS_DATA_TYPE_REL}]-(s:${SCHEMA_LABEL})
            OPTIONAL MATCH (d)<-[:${ONTO_HAS_DATA_TYPE_REL}]-(o:${ONTO_LABEL})

            RETURN
                d AS node,
                collect(DISTINCT s.${ID_PROP}) AS schemaIds,
                collect(DISTINCT o)           AS ontologies,
                total                         AS total
            """)
                .bind("DATA_LABEL", getNodeLabel())
                .bind("SCHEMA_LABEL", SchemaNodeDTO.nodeLabel())
                .bind("ONTO_LABEL", OntologyNodeDTO.nodeLabel())
                .bind("ID_PROP", idPropertyName)
                .bind("SCHEMA_HAS_DATA_TYPE_REL", InterNodeEdgeLabels.SCHEMA_TO_ONTOLOGY)
                .bind("ONTO_HAS_DATA_TYPE_REL", InterNodeEdgeLabels.DATA_TYPE_TO_ONTOLOGY)
                .build();

        return read(cypher, Values.parameters(
                "ids", ids,
                "skip", skip,
                "limit", pageSize
        ))
                .map(r -> new AbstractMap.SimpleEntry<>(
                        mapper.fromDetailRecord(r),
                        r.get("total").asLong(0L)
                ))
                .collect().asList()
                .onItem().transform(entries -> {
                    long total = entries.isEmpty() ? 0L : entries.getFirst().getValue();
                    var items = entries.stream().map(Map.Entry::getKey).toList();
                    return new PagedResponse<>(items, page, pageSize, total);
                });
    }

    public Multi<DataTypeSchemaEdgeDTO> getAllDataTypesForQuery(List<String> schemaIds) {
        if (schemaIds == null || schemaIds.isEmpty()) {
            return Multi.createFrom().empty();
        }

        List<String> uniqueSchemaIds = schemaIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        String cypher = CypherTemplate.of("""
                        MATCH (s:${SCHEMA_LABEL})
                        WHERE s.${ID_PROP} IN $schema_ids
                        
                        MATCH (s)-[:${RELATES_TO_DATA_TYPE_REL}]->(dt:${DATA_LABEL})
                        
                        RETURN
                            s.${ID_PROP}  AS schemaId,
                            dt.${ID_PROP} AS dataTypeId
                        """)
                .bind("SCHEMA_LABEL", SchemaNodeDTO.nodeLabel())
                .bind("DATA_LABEL", DataTypeNodeDTO.nodeLabel())
                .bind("ID_PROP", idPropertyName)
                .bind("RELATES_TO_DATA_TYPE_REL", InterNodeEdgeLabels.SCHEMA_TO_DATA_TYPE)
                .build();

        return read(cypher, Values.parameters("schema_ids", uniqueSchemaIds))
                .map(record -> new DataTypeSchemaEdgeDTO(
                        record.get("schemaId").asString(null),
                        record.get("dataTypeId").asString(null)
                ));
    }

    public Uni<Void> updateEdges(DataTypeNodeDTO dto) {
        UUID id = dto.getId();
        if (id == null) {
            return Uni.createFrom().failure(
                    new BadRequestException("DataTypeNode id must not be null for edge update"));
        }

        String cypher = CypherTemplate.of("""
                        MATCH (d:${DATA_LABEL} {${ID_PROP}: $id})
                        
                        OPTIONAL MATCH (s:${SCHEMA_LABEL})-[sr:${SCHEMA_TO_DATA_TYPE_REL}]->(d)
                        DELETE sr
                        
                        WITH d
                        
                        OPTIONAL MATCH (d)-[or:${DATA_TYPE_TO_ONTOLOGY_REL}]->(:${ONTO_LABEL})
                        DELETE or
                        
                        WITH d
                        
                        FOREACH (sid IN $schemaIds |
                            MERGE (s:${SCHEMA_LABEL} {${ID_PROP}: sid})
                            MERGE (s)-[:${SCHEMA_TO_DATA_TYPE_REL}]->(d)
                        )
                        
                        FOREACH (oid IN $ontologyIds |
                            MERGE (o:${ONTO_LABEL} {${ID_PROP}: oid})
                            MERGE (d)-[:${DATA_TYPE_TO_ONTOLOGY_REL}]->(o)
                        )
                        
                        RETURN d
                        """)
                .bind("DATA_LABEL", DataTypeNodeDTO.nodeLabel())
                .bind("SCHEMA_LABEL", SchemaNodeDTO.nodeLabel())
                .bind("ONTO_LABEL", OntologyNodeDTO.nodeLabel())
                .bind("ID_PROP", idPropertyName)
                .bind("SCHEMA_TO_DATA_TYPE_REL", InterNodeEdgeLabels.SCHEMA_TO_DATA_TYPE)
                .bind("DATA_TYPE_TO_ONTOLOGY_REL", InterNodeEdgeLabels.DATA_TYPE_TO_ONTOLOGY)
                .build();

        List<String> schemaIds = dto.getSchemaIds() != null
                ? dto.getSchemaIds().stream()
                .filter(Objects::nonNull)
                .toList()
                : List.of();

        List<String> ontologyIds = dto.getOntologyIds() != null
                ? dto.getOntologyIds().stream()
                .filter(Objects::nonNull)
                .toList()
                : List.of();

        var params = Values.parameters(
                "id", id.toString(),
                "schemaIds", schemaIds,
                "ontologyIds", ontologyIds
        );

        return write(cypher, params)
                .collect().last()
                .replaceWithVoid();
    }

    @Override
    protected String getNodeLabel() {
        return DataTypeNodeDTO.label(DataTypeNodeDTO.class);
    }

    @Override
    protected String getEdgeLabel() {
        return DataTypeEdgeDTO.label(DataTypeEdgeDTO.class);
    }
}
