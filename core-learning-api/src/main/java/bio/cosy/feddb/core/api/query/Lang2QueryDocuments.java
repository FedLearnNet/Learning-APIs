package bio.cosy.feddb.core.api.query;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.logging.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Reads and writes the stored {@code query_string}: either the legacy item array
 * or a Lang2Query group object. Leaves are also flattened for queryability.
 */
public final class Lang2QueryDocuments {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private Lang2QueryDocuments() {
    }

    public static void readInto(String json, QueryDTO dto) {
        ParsedQuery parsed = parse(json);
        dto.setQuery(parsed.items());
        dto.setLang2Query(parsed.group());
    }

    public static List<QueryItemDTO> itemsOf(String json) {
        return parse(json).items();
    }

    public static Lang2QueryNodeDTO groupOf(String json) {
        return parse(json).group();
    }

    public static String toQueryString(QueryDTO dto) {
        if (dto == null) {
            return "";
        }
        if (dto.getLang2Query() != null) {
            return writeJson(dto.getLang2Query());
        }
        return writeItems(dto.getQuery());
    }

    public static String toQueryString(List<QueryItemDTO> items, Lang2QueryNodeDTO group) {
        if (group != null) {
            return writeJson(group);
        }
        return writeItems(items);
    }

    public static List<QueryItemDTO> leavesOf(QueryDTO dto) {
        if (dto == null) {
            return List.of();
        }
        if (dto.getLang2Query() != null) {
            return flattenLeaves(dto.getLang2Query());
        }
        return dto.getQuery() == null ? List.of() : dto.getQuery();
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

    private static ParsedQuery parse(String json) {
        if (json == null || json.isBlank()) {
            return new ParsedQuery(Collections.emptyList(), null);
        }
        try {
            JsonNode tree = OBJECT_MAPPER.readTree(json);
            if (tree == null || tree.isNull() || tree.isMissingNode()) {
                return new ParsedQuery(Collections.emptyList(), null);
            }
            if (tree.isArray()) {
                List<QueryItemDTO> items = OBJECT_MAPPER.convertValue(
                        tree,
                        OBJECT_MAPPER.getTypeFactory().constructCollectionType(List.class, QueryItemDTO.class));
                return new ParsedQuery(items == null ? List.of() : items, null);
            }
            Lang2QueryNodeDTO group = OBJECT_MAPPER.treeToValue(tree, Lang2QueryNodeDTO.class);
            return new ParsedQuery(flattenLeaves(group), group);
        } catch (Exception e) {
            Log.error("Error parsing query document", e);
            return new ParsedQuery(new ArrayList<>(), null);
        }
    }

    private static String writeItems(List<QueryItemDTO> queryItems) {
        if (queryItems == null || queryItems.isEmpty()) {
            return "";
        }
        return writeJson(queryItems);
    }

    private static String writeJson(Object value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            Log.error("Error converting query document to JSON string", e);
            return "";
        }
    }

    private record ParsedQuery(List<QueryItemDTO> items, Lang2QueryNodeDTO group) {
    }
}
