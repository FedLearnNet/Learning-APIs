package bio.cosy.feddb.local.api.importer.files.table;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class TableSampleTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void survivesAJsonRoundTripLikeTheStoredPreview() throws Exception {
        TableSample sample = new TableSample(List.of("gender"),
                List.of(Map.of("gender", "Male"), Map.of("gender", "Female")), null);

        String json = objectMapper.writeValueAsString(Map.of("0", sample));
        Map<String, TableSample> read = objectMapper.readValue(json, new TypeReference<>() {
        });

        assertFalse(json.contains("\"values\""), json);
        assertEquals(sample.getRows(), read.get("0").getRows());
        assertEquals(List.of("Male", "Female"), read.get("0").getValues().get("gender"));
    }
}
