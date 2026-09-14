package de.unihamburg.daibetes.base;

import bio.cosy.feddb.core.api.datamodler.BaseEdgeDTO;
import bio.cosy.feddb.core.api.datamodler.BaseNodeDTO;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.neo4j.driver.Record;
import org.neo4j.driver.Value;
import org.neo4j.driver.types.Node;
import org.neo4j.driver.types.Relationship;

import java.time.LocalDate;
import java.util.*;
import java.util.function.Consumer;

public abstract class BaseMapper<NODE extends BaseNodeDTO, EDGE extends BaseEdgeDTO> {

    @ConfigProperty(name = "feddb.neo4j.id-property", defaultValue = "id")
    protected String idPropertyName;

    public abstract NODE fromNode(NODE dto, Node node);

    public abstract EDGE fromEdge(EDGE dto, Relationship edge);

    public abstract Map<String, Object> toNodeProperties(Map<String, Object> map, NODE dto);

    public abstract Map<String, Object> toEdgeProperties(Map<String, Object> map, EDGE dto);

    public abstract NODE newNode();

    public abstract EDGE newEdge();

    public NODE fromNode(Node node) {
        NODE dto = newNode();
        setId(node, idPropertyName, dto::setId);
        setString(node, "label", dto::setLabel);
        Iterable<String> labels = node.labels();
        dto.setLabel(labels.iterator().hasNext() ? labels.iterator().next() : null);
        return fromNode(dto, node);
    }

    public EDGE fromEdge(Relationship edge, String sourceId, String targetId) {
        EDGE dto = newEdge();
        dto.setSourceId(sourceId);
        dto.setTargetId(targetId);
        dto.setLabel(edge.type());
        setId(edge, idPropertyName, dto::setId);
        return fromEdge(dto, edge);
    }

    public Map<String, Object> toNodeProperties(NODE dto) {
        Map<String, Object> props = new HashMap<>();

        dto.setIdIfAbsent();
        setValue(props, idPropertyName, dto.getId().toString());
        setValue(props, "label", dto.getLabel());
        return toNodeProperties(props, dto);
    }

    public Map<String, Object> toEdgeProperties(EDGE dto) {
        Map<String, Object> props = new HashMap<>();
        dto.setIdIfAbsent();

        setValue(props, idPropertyName, dto.getId().toString());
        setValue(props, "sourceId", dto.getSourceId());
        setValue(props, "targetId", dto.getTargetId());
        return toEdgeProperties(props, dto);
    }

    public NODE fromNodeRecord(Record record) {
        Value nodeValue;
        if (record.containsKey("node")) {
            nodeValue = record.get("node");
        } else if (record.containsKey("n")) {
            nodeValue = record.get("n");
        } else {
            nodeValue = record.values().get(0);
        }

        Node node = nodeValue.asNode();
        return fromNode(node);
    }

    protected NODE fromNodeRecord(Record record, String key) {
        if (record.containsKey(key)) {
            Value nodeValue = record.get(key);
            Node node = nodeValue.asNode();
            return fromNode(node);
        }
        return null;
    }

    protected EDGE fromEdgeRecord(Record record) {
        Value nodeValue;
        if (record.containsKey("edge")) {
            nodeValue = record.get("edge");
        } else if (record.containsKey("e")) {
            nodeValue = record.get("e");
        } else if (record.containsKey("r")) {
            nodeValue = record.get("r");
        } else {
            nodeValue = record.values().get(0);
        }

        Relationship node = nodeValue.asRelationship();
        String sourceId = node.startNodeElementId();
        String targetId = node.endNodeElementId();
        if (record.containsKey("sourceId")) {
            sourceId = record.get("sourceId").asString();
        }
        if (record.containsKey("targetId")) {
            targetId = record.get("targetId").asString();
        }
        return fromEdge(node, sourceId, targetId);
    }

    public List<NODE> fromNode(List<Node> records) {
        return records.stream()
                .map(this::fromNode)
                .toList();
    }

    public List<NODE> fromNodeRecords(List<Node> records, String key) {
        return records.stream()
                .map(this::fromNode)
                .toList();
    }

    public List<Map<String, Object>> toNodeProperties(List<NODE> dtos) {
        return dtos.stream()
                .map(this::toNodeProperties)
                .toList();
    }


    public List<Map<String, Object>> toEdgeProperties(List<EDGE> dtos) {
        return dtos.stream()
                .map(this::toEdgeProperties)
                .toList();
    }


    public void setString(Node node, String key, Consumer<String> setter) {
        if (node.containsKey(key)) {
            setter.accept(node.get(key).asString(null));
        }
    }

    public void setString(Relationship node, String key, Consumer<String> setter) {
        if (node.containsKey(key)) {
            setter.accept(node.get(key).asString(null));
        }
    }

    public void setInt(Node node, String key, Consumer<Integer> setter) {
        if (node.containsKey(key)) {
            setter.accept(node.get(key).asInt());
        }
    }

    public void setDate(Node node, String key, Consumer<LocalDate> setter) {
        if (node.containsKey(key)) {
            setter.accept(node.get(key).asLocalDate(null));
        }
    }


    public void setBoolean(Node node, String key, Consumer<Boolean> setter) {
        if (node.containsKey(key)) {
            setter.accept(node.get(key).asBoolean());
        }
    }

    public void setId(Node node, String key, Consumer<UUID> setter) {
        if (node.containsKey(key)) {
            setter.accept(UUID.fromString(node.get(key).asString(null)));
        }
    }

    public void setString(Record node, String key, Consumer<String> setter) {
        if (node.containsKey(key)) {
            setter.accept(node.get(key).asString(null));
        }
    }

    public void setId(Relationship node, String key, Consumer<UUID> setter) {
        if (node.containsKey(key)) {
            setter.accept(UUID.fromString(node.get(key).asString(null)));
        }
    }

    public void setList(Record node, String key, Consumer<List> setter) {
        if (node.containsKey(key)) {
            setter.accept(node.get(key).asList());
        }
    }

    public void setValue(Map<String, Object> map, String key, Object value) {
        if (value != null) {
            map.put(key, value);
        }
    }

    public void setHasSet(Node node, String key, Consumer<Set> setter) {
        if (node.containsKey(key)) {
            setter.accept(new HashSet<>(node.get(key).asList()));
        }
    }

    public void setList(Node node, String key, Consumer<List> setter) {
        if (node.containsKey(key)) {
            setter.accept(node.get(key).asList());
        }
    }


    public void setSet(Map<String, Object> map, String key, Set value) {
        if (value != null) {
            map.put(key, value.stream().toList());
        }
    }

}
