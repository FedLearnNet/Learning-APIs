package bio.cosy.feddb.local.api.importer.connector;

import bio.cosy.feddb.local.api.importer.extract.UploadInfoDTO;
import bio.cosy.feddb.local.api.importer.files.ConnectorFileUploadInfoDTO;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.ObjectCodec;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

public class SafeUploadInfoDeserializer extends JsonDeserializer<Map<String, UploadInfoDTO>> {

    @Override
    public Map<String, UploadInfoDTO> deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        ObjectCodec codec = p.getCodec();
        JsonNode node = codec.readTree(p);

        if (node == null || node.isNull() || !node.isObject()) {
            return null;
        }

        Map<String, UploadInfoDTO> result = new LinkedHashMap<>();

        // Case 1:
        // ist a plain UploadInfoDTO
        UploadInfoDTO single = tryParse(codec, node);
        if (single != null) {
            result.put(ConnectorFileUploadInfoDTO.DEFAULT_SHEET_NAME, single);
            return result;
        }

        // Fall 2:
        // case two Map<String, UploadInfoDTO>
        for (Map.Entry<String, JsonNode> entry : node.properties()) {
            UploadInfoDTO dto = tryParse(codec, entry.getValue());
            if (dto != null) {
                result.put(entry.getKey(), dto);
            }
        }

        return result.isEmpty() ? null : result;
    }

    private UploadInfoDTO tryParse(ObjectCodec codec, JsonNode node) {
        try {
            if (node == null || node.isNull() || !node.isObject()) {
                return null;
            }
            return codec.treeToValue(node, UploadInfoDTO.class);
        } catch (Exception e) {
            return null;
        }
    }
}
