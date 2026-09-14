package bio.cosy.feddb.local.api.importer.files.table;

import java.util.List;
import java.util.Map;

/** Contiguous rows sharing the same raw grouping-column value. */
public record TableDataGroup(String key, List<Map<String, Object>> rows) {
}
