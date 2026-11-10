package bio.cosy.feddb.local.api.importer.run.preview.cache;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ConnectorPreviewCacheRowOrderTest {

    @Test
    void cachedRowsComeBackInTheirStoredColumnOrder() {
        // jsonb keeps no key order (shorter keys first, then byte order), so the stored rows lose
        // the column order the preview had; the stored column list still has it.
        Map<String, Object> jsonbRow = new LinkedHashMap<>();
        jsonbRow.put("sex", "f");
        jsonbRow.put("added", 3);
        jsonbRow.put("patient_id", "p1");
        jsonbRow.put("date_of_birth", null);
        ConnectorPreviewTransformationCacheEntity entity = new ConnectorPreviewTransformationCacheEntity();
        entity.setRows(new ArrayList<>(List.of(jsonbRow)));
        entity.setColumns(List.of("patient_id", "date_of_birth", "sex"));

        ConnectorPreviewTransformationCacheBO cache = new ConnectorPreviewTransformationCacheBO();
        cache.ao = mock(ConnectorPreviewTransformationCacheAO.class);
        when(cache.ao.find(3L, 0, "fingerprint")).thenReturn(Optional.of(entity));

        Map<String, Object> row = cache.find(3L, 0, "fingerprint").orElseThrow().getRows().getFirst();

        // A key the column list does not name stays in the row, after the listed ones.
        assertEquals(List.of("patient_id", "date_of_birth", "sex", "added"), new ArrayList<>(row.keySet()));
        assertTrue(row.containsKey("date_of_birth"));
        assertNull(row.get("date_of_birth"));
        assertEquals("p1", row.get("patient_id"));
        assertEquals(3, row.get("added"));
    }
}
