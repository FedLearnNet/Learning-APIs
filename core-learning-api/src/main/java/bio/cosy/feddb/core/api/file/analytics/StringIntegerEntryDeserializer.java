package bio.cosy.feddb.core.api.file.analytics;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;

import java.io.IOException;
import java.util.AbstractMap;
import java.util.Map;

/**
 * Reconstructs the string/integer entries used by {@link ColumnProfile}.
 *
 * <p>Jackson serializes a {@link Map.Entry} as a single-property JSON object, but cannot
 * instantiate the interface when reading it. The additional shapes keep cached values
 * compatible with clients and with the short-lived cache DTO representation.</p>
 */
public final class StringIntegerEntryDeserializer
        extends StdDeserializer<Map.Entry<String, Integer>> {

    public StringIntegerEntryDeserializer() {
        super(Map.Entry.class);
    }

    @Override
    public Map.Entry<String, Integer> deserialize(JsonParser parser, DeserializationContext context)
            throws IOException {
        JsonNode node = parser.getCodec().readTree(parser);

        if (node.isArray() && node.size() == 2) {
            return entry(parser, node.get(0), node.get(1));
        }

        if (node.isObject()) {
            if (node.has("key") && node.has("value")) {
                return entry(parser, node.get("key"), node.get("value"));
            }
            if (node.has("value") && node.has("count")) {
                return entry(parser, node.get("value"), node.get("count"));
            }
            if (node.size() == 1) {
                Map.Entry<String, JsonNode> field = node.properties().iterator().next();
                return new AbstractMap.SimpleImmutableEntry<>(
                        field.getKey(),
                        integerValue(parser, field.getValue())
                );
            }
        }

        throw JsonMappingException.from(parser, "Expected a string/integer entry");
    }

    private Map.Entry<String, Integer> entry(JsonParser parser, JsonNode key, JsonNode value)
            throws JsonMappingException {
        if (key == null || key.isNull() || !key.isValueNode()) {
            throw JsonMappingException.from(parser, "Entry key must be a string value");
        }
        return new AbstractMap.SimpleImmutableEntry<>(key.asText(), integerValue(parser, value));
    }

    private Integer integerValue(JsonParser parser, JsonNode value) throws JsonMappingException {
        if (value != null && value.isIntegralNumber() && value.canConvertToInt()) {
            return value.intValue();
        }
        throw JsonMappingException.from(parser, "Entry value must be an integer");
    }
}
