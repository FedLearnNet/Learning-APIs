package de.unihamburg.daibetes.api.schema;


import bio.cosy.feddb.core.api.datamodler.datatype.DataTypeNodeDTO;
import bio.cosy.feddb.core.api.datamodler.ontology.OntologyNodeDTO;
import bio.cosy.feddb.core.api.datamodler.schema.SchemaEdgeDTO;
import bio.cosy.feddb.core.api.datamodler.schema.SchemaNodeDTO;
import bio.cosy.feddb.core.api.datamodler.schema.SchemaNodeDetailDTO;
import bio.cosy.feddb.core.api.datamodler.schema.SchemaNodeType;
import bio.cosy.feddb.core.api.datamodler.schema.SchemaStructureDTO;
import de.unihamburg.daibetes.api.datatype.DataTypeMapper;
import de.unihamburg.daibetes.api.ontology.OntologyMapper;
import de.unihamburg.daibetes.base.BaseMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.neo4j.driver.Record;
import org.neo4j.driver.Value;
import org.neo4j.driver.types.Node;
import org.neo4j.driver.types.Relationship;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class SchemaMapper extends BaseMapper<SchemaNodeDTO, SchemaEdgeDTO> {

    @Inject
    OntologyMapper ontologyMapper;

    @Inject
    DataTypeMapper dataTypeMapper;

    @Override
    public SchemaNodeDTO fromNode(SchemaNodeDTO dto, Node node) {
        setString(node, "name", dto::setName);
        setString(node, "description", dto::setDescription);
        setString(node, "type", dto::setType);

        setDate(node, "createdAt", dto::setCreatedAt);
        setDate(node, "updatedAt", dto::setUpdatedAt);
        setInt(node, "version", dto::setVersion);
        setBoolean(node, "isRoot", dto::setRoot);

        if (dto.isRoot()) {
            setList(node, "subscriptions", dto::setSubscriptions);
            setString(node, "createdBy", dto::setCreatedBy);
        }
        return dto;
    }

    @Override
    public SchemaEdgeDTO fromEdge(SchemaEdgeDTO dto, Relationship edge) {
        return dto;
    }

    @Override
    public Map<String, Object> toNodeProperties(Map<String, Object> map, SchemaNodeDTO dto) {
        setValue(map, "description", dto.getDescription());
        setValue(map, "name", dto.getName());
        setValue(map, "type", dto.getType().toString());
        setValue(map, "createdAt", dto.getCreatedAt());
        setValue(map, "updatedAt", dto.getUpdatedAt());
        setValue(map, "version", dto.getVersion());
        setValue(map, "isRoot", dto.isRoot());

        if (dto.isRoot()) {
            setValue(map, "subscriptions", dto.getSubscriptions());
            setValue(map, "createdBy", dto.getCreatedBy());
        }
        return map;
    }

    @Override
    public Map<String, Object> toEdgeProperties(Map<String, Object> map, SchemaEdgeDTO dto) {
        return map;
    }

    public SchemaNodeDetailDTO fromDetailRecord(Record record) {
        SchemaNodeDetailDTO dto = new SchemaNodeDetailDTO();
        if (record.containsKey("node") && !record.get("node").isNull()) {
            Node node = record.get("node").asNode();
            setId(node, idPropertyName, dto::setId);
            setString(node, "label", dto::setLabel);
            fromNode(dto, node);
        }

        if (record.containsKey("ontology") && !record.get("ontology").isNull()) {
            Optional.of(record.get("ontology")).map(Value::asNode).ifPresent(oNode -> {
                OntologyNodeDTO ontologyDTO = ontologyMapper.fromNode(oNode);
                dto.setOntology(ontologyDTO);
                dto.setOntologyId(ontologyDTO.getId());
            });
        }

        if (record.containsKey("dataType") && !record.get("dataType").isNull()) {
            Optional.of(record.get("dataType")).map(Value::asNode).ifPresent(oNode -> {
                DataTypeNodeDTO dataTypeDTO = dataTypeMapper.fromNode(oNode);
                dto.setDataType(dataTypeDTO);
                dto.setDataTypeId(dataTypeDTO.getId());
            });
        }

        if (record.containsKey("parentId") && !record.get("parentId").isNull()) {
            dto.setParentId(UUID.fromString(record.get("parentId").asString()));
        }

        if (record.containsKey("childrenIds") && !record.get("childrenIds").isNull()) {
            dto.setChildrenIds(
                    record.get("childrenIds").asList(Value::asString).stream().map(UUID::fromString).toList()
            );
        }
        return dto;
    }


    public SchemaStructureDTO toStructureDTO(SchemaNodeDetailDTO detail) {
        SchemaStructureDTO dto = new SchemaStructureDTO();

        // 1. Common Fields that get copied for ANY node
        dto.setId(detail.getId());
        dto.setCreatedAt(detail.getCreatedAt());
        dto.setUpdatedAt(detail.getUpdatedAt());
        dto.setVersion(detail.getVersion());

        dto.setLabel(detail.getLabel());
        dto.setName(detail.getName());
        dto.setDescription(detail.getDescription());
        dto.setType(detail.getType());

        // 2. Root-Specific Fields (Conditional Mapping)
        // We use the Type Enum as the source of truth
        if (detail.getType() == SchemaNodeType.ROOT) {
            dto.setRoot(true);
            dto.setCreatedBy(detail.getCreatedBy());
            dto.setSubscriptions(detail.getSubscriptions());
        } else {
            // Explicitly ensure non-root nodes don't carry root flags
            dto.setRoot(false);
        }

        // 3. References and the related objects
        dto.setOntologyId(detail.getOntologyId());
        dto.setDataTypeId(detail.getDataTypeId());
        dto.setParentId(detail.getParentId());
        dto.setChildrenIds(detail.getChildrenIds());
        dto.setOntology(detail.getOntology());
        dto.setDataType(detail.getDataType());

        // Initialize children list
        dto.setChildren(List.of());

        dto.setSubscriptions(detail.getSubscriptions());
        return dto;
    }

    @Override
    public SchemaNodeDTO newNode() {
        return new SchemaNodeDTO();
    }

    @Override
    public SchemaEdgeDTO newEdge() {
        return new SchemaEdgeDTO();
    }
}
