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

    /**
     * Parses a stored query document and assigns it to the DTO.
     *
     * @param json stored {@code query_string}; blank input clears the query
     * @param dto query to update
     */
    public static void readInto(final String json, final QueryDTO dto) {
        dto.setQuery(parse(json));
    }

    /**
     * Parses a stored query document into a Lang2Query tree.
     * A legacy item array is wrapped as {@code $and}.
     *
     * @param json stored {@code query_string}
     * @return the parsed tree, or {@code null} when the document is blank or invalid
     */
    public static Lang2QueryNodeDTO parse(final String json) {
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

    /**
     * Serializes a query DTO to the stored JSON document.
     *
     * @param dto query to write
     * @return JSON text, or an empty string when the DTO is missing
     */
    public static String toQueryString(final QueryDTO dto) {
        if (dto == null) {
            return "";
        }
        return toQueryString(dto.getQuery());
    }

    /**
     * Serializes a Lang2Query tree to JSON.
     *
     * @param node tree to write
     * @return JSON text, or an empty string when the node is missing
     */
    public static String toQueryString(final Lang2QueryNodeDTO node) {
        if (node == null) {
            return "";
        }
        return writeJson(node);
    }

    /**
     * Returns the leaf conditions of a query, ignoring group structure.
     *
     * @param dto query to flatten
     * @return leaf conditions, or an empty list when the query is missing
     */
    public static List<QueryItemDTO> leavesOf(final QueryDTO dto) {
        if (dto == null) {
            return List.of();
        }
        return flattenLeaves(dto.getQuery());
    }

    /**
     * Returns the leaf conditions under a node, ignoring group structure.
     *
     * @param node tree to flatten
     * @return leaf conditions, or an empty list when the node is missing
     */
    public static List<QueryItemDTO> flattenLeaves(final Lang2QueryNodeDTO node) {
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

    /**
     * Wraps leaf items in an {@code $and} group.
     *
     * @param items leaves to combine; {@code null} becomes an empty group
     * @return the group node
     */
    public static Lang2QueryNodeDTO andGroup(final QueryItemDTO... items) {
        return andGroup(items == null ? List.of() : List.of(items));
    }

    /**
     * Wraps leaf items in an {@code $and} group.
     *
     * @param items leaves to combine; {@code null} or empty becomes an empty group
     * @return the group node
     */
    public static Lang2QueryNodeDTO andGroup(final List<QueryItemDTO> items) {
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

    /**
     * Converts one leaf to the flat query-item shape used by queryability.
     *
     * @param leaf leaf condition
     * @return the item, or {@code null} when the leaf is missing
     */
    public static QueryItemDTO toQueryItem(final Lang2QueryNodeDTO leaf) {
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

    /**
     * Converts a flat query item back into a leaf, or an {@code $and} group when it has several operators.
     *
     * @param item item to convert
     * @return the corresponding tree node
     */
    public static Lang2QueryNodeDTO fromQueryItem(final QueryItemDTO item) {
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

    private static Lang2QueryNodeDTO leafFrom(final QueryItemDTO item, final QueryOperatorDTO operator) {
        Lang2QueryNodeDTO leaf = new Lang2QueryNodeDTO();
        leaf.setOntologyId(item.getOntologyId());
        leaf.setDataTypeId(item.getDataTypeId());
        if (operator != null && operator.getOperator() != null) {
            leaf.setOperator(operator.getOperator().lang2Operator());
            leaf.setValue(operator.getValue());
        }
        return leaf;
    }

    private static String writeJson(final Object value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            Log.error("Error converting query document to JSON string", e);
            return "";
        }
    }
}
