package bio.cosy.feddb.core.api.query.item;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import io.quarkus.logging.Log;
import tools.jackson.core.JacksonException;

import java.util.List;

// stored query_string <-> query tree
public final class QueryItemHelper {

    // dates as YYYY-MM-DD, like the REST API
    private static final ObjectMapper OBJECT_MAPPER = new JsonMapper();

    private QueryItemHelper() {
    }

    // null for a blank or invalid document
    public static QueryItemDTO fromJson(final String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readValue(json, QueryItemDTO.class);
        } catch (Exception e) {
            Log.error("Error parsing query document", e);
            return null;
        }
    }

    // empty for a missing tree
    public static String toJson(final QueryItemDTO query) {
        if (query == null) {
            return "";
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(query);
        } catch (JacksonException e) {
            Log.error("Error converting query document to JSON string", e);
            return "";
        }
    }

    // empty for a missing tree
    public static List<QueryItemDTO> leavesOf(final QueryItemDTO query) {
        return query == null ? List.of() : query.leaves();
    }

    public static QueryItemDTO andGroup(final QueryItemDTO... items) {
        return andGroup(items == null ? List.of() : List.of(items));
    }

    public static QueryItemDTO andGroup(final List<QueryItemDTO> items) {
        QueryItemDTO group = new QueryItemDTO();
        group.setConnectingOperator(ConnectingOperator.AND);
        group.setConditions(items == null ? List.of() : List.copyOf(items));
        return group;
    }
}
