package bio.cosy.feddb.local.api.importer.run.preview;

import bio.cosy.feddb.core.api.file.analytics.RowProfiler;

import java.util.Collection;
import java.util.Map;

/**
 * What the connector preview treats as an empty cell when it looks for rows that show data.
 * Same tokens as the column profile's missing count, plus {@code ?}, which many exported data sets
 * use for "unknown". The profile statistics themselves are not changed.
 */
public final class PreviewCells {

    private PreviewCells() {
    }

    public static boolean isEmpty(Object value) {
        if (value == null) {
            return true;
        }
        if (value instanceof Collection<?> collection) {
            return collection.isEmpty();
        }
        if (value instanceof Map<?, ?> map) {
            return map.isEmpty();
        }
        String text = String.valueOf(value);
        return RowProfiler.isMissing(text) || "?".equals(text.trim());
    }

    public static boolean hasValue(Map<String, Object> row, String column) {
        return row != null && !isEmpty(row.get(column));
    }
}
