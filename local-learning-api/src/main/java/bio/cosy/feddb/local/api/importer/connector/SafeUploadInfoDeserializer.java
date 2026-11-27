package bio.cosy.feddb.local.api.importer.connector;

import bio.cosy.feddb.local.api.importer.extract.UploadInfoDTO;
import bio.cosy.feddb.local.api.importer.files.ConnectorFileUploadInfoDTO;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ValueDeserializer;

import java.util.LinkedHashMap;
import java.util.Map;

public class SafeUploadInfoDeserializer extends ValueDeserializer<Map<String, UploadInfoDTO>> {

    @Override
    public Map<String, UploadInfoDTO> deserialize(JsonParser p, DeserializationContext ctxt) {
        JsonNode node = ctxt.readTree(p);

        if (node == null || node.isNull() || !node.isObject()) {
            return null;
        }

        Map<String, UploadInfoDTO> result = new LinkedHashMap<>();

        // Case 1:
        // ist a plain UploadInfoDTO
        UploadInfoDTO single = tryParse(ctxt, node);
        if (single != null) {
            result.put(ConnectorFileUploadInfoDTO.DEFAULT_SHEET_NAME, single);
            return result;
        }

        // Fall 2:
        // case two Map<String, UploadInfoDTO>
        for (Map.Entry<String, JsonNode> entry : node.properties()) {
            UploadInfoDTO dto = tryParse(ctxt, entry.getValue());
            if (dto != null) {
                result.put(entry.getKey(), dto);
            }
        }

        return result.isEmpty() ? null : result;
    }

    private UploadInfoDTO tryParse(DeserializationContext ctxt, JsonNode node) {
        try {
            if (node == null || node.isNull() || !node.isObject()) {
                return null;
            }
            return ctxt.readTreeAsValue(node, UploadInfoDTO.class);
        } catch (Exception e) {
            return null;
        }
    }
}
