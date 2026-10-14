package bio.cosy.feddb.core.api.query;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.logging.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * Reads and writes the stored {@code query_string} as a Lang2Query tree.
 * Legacy item arrays are wrapped as {@code $and} so the API always sees an object.
 * Leaves are flattened only for queryability / enhance.
 */
public final class Lang2QueryDocuments {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private Lang2QueryDocuments() {
    }

    public static void readInto(String json, QueryDTO dto) {
        dto.setQuery(parse(json));
    }

    public static Lang2QueryNodeDTO parse(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            JsonNode tree = OBJECT_MAPPER.readTree(json);
            if (tree == null || tree.isNull() || tree.isMissingNode()) {
                return null;
            }
            if (tree.isArray()) {
                List<QueryItemDTO> items = OBJECT_MAPPER.convertValue(
                        tree,
                        OBJECT_MAPPER.getTypeFactory().constructCollectionType(List.class, QueryItemDTO.class));
                return andGroup(items == null ? List.of() : items);
            }
            return OBJECT_MAPPER.treeToValue(tree, Lang2QueryNodeDTO.class);
        } catch (Exception e) {
            Log.error("Error parsing query document", e);
            return null;
        }
    }

    public static String toQueryString(QueryDTO dto) {
        if (dto == null) {
            return "";
        }
        return toQueryString(dto.getQuery());
    }

    public static String toQueryString(Lang2QueryNodeDTO node) {
        if (node == null) {
            return "";
        }
        return writeJson(node);
    }

    public static List<QueryItemDTO> leavesOf(QueryDTO dto) {
        if (dto == null) {
            return List.of();
        }
        return flattenLeaves(dto.getQuery());
    }

    public static List<QueryItemDTO> flattenLeaves(Lang2QueryNodeDTO node) {
        if (node == null) {
            return List.of();
        }
        if (node.isGroup()) {
            if (node.getConditions() == null) {
                return List.of();
            }
            List<QueryItemDTO> leaves = new ArrayList<>();
            for (Lang2QueryNodeDTO child : node.getConditions()) {
                leaves.addAll(flattenLeaves(child));
            }
            return leaves;
        }
        QueryItemDTO item = toQueryItem(node);
        return item == null ? List.of() : List.of(item);
    }

    public static Lang2QueryNodeDTO andGroup(QueryItemDTO... items) {
        return andGroup(items == null ? List.of() : List.of(items));
    }

    public static Lang2QueryNodeDTO andGroup(List<QueryItemDTO> items) {
        Lang2QueryNodeDTO group = new Lang2QueryNodeDTO();
        group.setConnectingOperator("$and");
        if (items == null || items.isEmpty()) {
            group.setConditions(List.of());
            return group;
        }
        List<Lang2QueryNodeDTO> conditions = new ArrayList<>();
        for (QueryItemDTO item : items) {
            conditions.add(fromQueryItem(item));
        }
        group.setConditions(conditions);
        return group;
    }

    public static QueryItemDTO toQueryItem(Lang2QueryNodeDTO leaf) {
        if (leaf == null) {
            return null;
        }
        QueryItemDTO item = new QueryItemDTO();
        item.setOntologyId(leaf.getOntologyId());
        item.setDataTypeId(leaf.getDataTypeId());
        QueryOperatorTypes operator = QueryOperatorTypes.fromOperator(leaf.getOperator());
        if (operator != null) {
            QueryOperatorDTO operatorDto = new QueryOperatorDTO();
            operatorDto.setOperator(operator);
            operatorDto.setValue(leaf.valueAsString());
            item.setOperator(List.of(operatorDto));
        }
        return item;
    }

    public static Lang2QueryNodeDTO fromQueryItem(QueryItemDTO item) {
        if (item == null) {
            return new Lang2QueryNodeDTO();
        }
        List<QueryOperatorDTO> operators = item.getOperator();
        if (operators == null || operators.isEmpty()) {
            return leafFrom(item, null);
        }
        if (operators.size() == 1) {
            return leafFrom(item, operators.getFirst());
        }
        Lang2QueryNodeDTO and = new Lang2QueryNodeDTO();
        and.setConnectingOperator("$and");
        List<Lang2QueryNodeDTO> conditions = new ArrayList<>();
        for (QueryOperatorDTO operator : operators) {
            conditions.add(leafFrom(item, operator));
        }
        and.setConditions(conditions);
        return and;
    }

    private static Lang2QueryNodeDTO leafFrom(QueryItemDTO item, QueryOperatorDTO operator) {
        Lang2QueryNodeDTO leaf = new Lang2QueryNodeDTO();
        leaf.setOntologyId(item.getOntologyId());
        leaf.setDataTypeId(item.getDataTypeId());
        if (operator != null && operator.getOperator() != null) {
            leaf.setOperator(operator.getOperator().name());
            leaf.setValue(operator.getValue());
        }
        return leaf;
    }

    private static String writeJson(Object value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            Log.error("Error converting query document to JSON string", e);
            return "";
        }
    }
}
