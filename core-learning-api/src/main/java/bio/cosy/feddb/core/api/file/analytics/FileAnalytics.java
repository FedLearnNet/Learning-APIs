package bio.cosy.feddb.core.api.file.analytics;

import bio.cosy.feddb.core.api.app.config.ToolConfigDataType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Builds column profiles for tabular input that arrives as a document rather than as rows.
 *
 * <p>The accumulation itself lives in {@link RowProfiler}; this class only adapts the input shapes
 * onto it. A reader that produces the rows itself should feed a {@link ShardedRowProfiler} while it
 * parses instead, so the file is walked once rather than materialised and read back.</p>
 */
@ApplicationScoped
public class FileAnalytics {

    public FileProfile profile(String fileContent, ToolConfigDataType fileType) {
        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setAllowMissingColumnNames(true)   // <<< allows empty headers
                .setHeader()                        // use the first row as header
                .setSkipHeaderRecord(true)          // do not return the header as a record
                .setTrim(true)
                .setIgnoreEmptyLines(true)
                .setDelimiter(fileType == ToolConfigDataType.TSV ? '\t' : ',')
                .get();

        try (Reader in = new StringReader(fileContent);
             CSVParser parser = CSVParser.builder()
                     .setReader(in)
                     .setFormat(format)
                     .get()) {

            // Raw headers from the parser (may be empty/duplicated)
            final List<String> rawHeaders = parser.getHeaderNames();
            if (rawHeaders == null || rawHeaders.isEmpty()) {
                throw new IllegalStateException("No header row detected in string");
            }

            // Generate sanitized, unique names
            final List<String> headers = sanitizeHeaders(rawHeaders);
            RowProfiler profiler = RowProfiler.of(headers);

            for (CSVRecord rec : parser) {
                profiler.startRow();
                for (int i = 0; i < headers.size(); i++) {
                    profiler.accept(i, i < rec.size() ? rec.get(i) : null);
                }
                profiler.endRow();
            }

            return profiler.finish();

        } catch (IOException e) {
            throw new RuntimeException("Failed to read file string", e);
        }
    }

    public FileProfile profileJson(String jsonContent) {
        if (jsonContent == null || jsonContent.isBlank()) {
            return new FileProfile("", 0L, List.of(), List.of());
        }

        final ObjectMapper mapper = new ObjectMapper();
        final JsonNode root;
        try {
            root = mapper.readTree(jsonContent);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid JSON content", e);
        }

        // Support either: (1) JSON array of objects, or (2) a single object.
        final List<JsonNode> rows;
        if (root.isArray()) {
            rows = new ArrayList<>();
            root.forEach(rows::add);
        } else {
            rows = List.of(root);
        }

        // Collect headers in first-seen order (union of keys across rows)
        LinkedHashSet<String> rawKeys = new LinkedHashSet<>();
        for (JsonNode r : rows) {
            if (r != null && r.isObject()) {
                r.fieldNames().forEachRemaining(rawKeys::add);
            }
        }

        if (rawKeys.isEmpty()) {
            // No object keys found -> treat as single "value" column
            rawKeys.add("value");
        }

        final List<String> headers = sanitizeHeaders(new ArrayList<>(rawKeys));
        final Map<String, String> sanitizedToRaw = buildSanitizedToRawKey(rawKeys, headers);
        RowProfiler profiler = RowProfiler.of(headers);

        for (JsonNode r : rows) {
            profiler.startRow();
            for (int i = 0; i < headers.size(); i++) {
                profiler.acceptJson(i, extractJsonValue(r, headers.get(i), sanitizedToRaw));
            }
            profiler.endRow();
        }

        return profiler.finish();
    }

    /**
     * Profiles already parsed rows without serializing the complete table to an
     * intermediate JSON document. Prefer {@link #newProfiler} where the caller is the one producing
     * the rows: this overload exists for tables that were built elsewhere, and walking them here
     * means reading them a second time.
     */
    public FileProfile profileRows(List<String> columns, Stream<Map<String, Object>> rows) {
        return profileRows(columns, rows, null);
    }

    /**
     * @param source       what is being profiled, for the progress log
     */
    public FileProfile profileRows(
            List<String> columns,
            Stream<Map<String, Object>> rows,
            String source
    ) {
        RowProfiler profiler = RowProfiler.of(columns, source);
        rows.forEach(profiler::acceptRow);
        return profiler.finish();
    }

    /**
     * Maps each sanitized header back to the raw key it came from, by positional order.
     * Built once per profiling run so that per-cell lookups stay O(1).
     */
    private static Map<String, String> buildSanitizedToRawKey(LinkedHashSet<String> rawKeys, List<String> headers) {
        Map<String, String> out = new HashMap<>();
        int idx = 0;
        for (String raw : rawKeys) {
            if (idx >= headers.size()) break;
            out.put(headers.get(idx), raw);
            idx++;
        }
        return out;
    }

    private static JsonNode extractJsonValue(JsonNode row, String sanitizedHeader, Map<String, String> sanitizedToRaw) {
        if (row == null) return null;

        // Common case: keys already safe/unique -> direct lookup by header
        if (row.isObject() && row.has(sanitizedHeader)) {
            return row.get(sanitizedHeader);
        }

        // Fallback: the header was sanitized (empty/duplicate key) -> resolve via positional mapping
        if (row.isObject()) {
            String raw = sanitizedToRaw.get(sanitizedHeader);
            return raw == null ? null : row.get(raw);
        }

        // Non-object rows -> single value
        return row;
    }

    // ---------- Header sanitization ----------

    private static List<String> sanitizeHeaders(List<String> raw) {
        List<String> out = new ArrayList<>(raw.size());
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < raw.size(); i++) {
            String h = raw.get(i);
            if (h != null) {
                h = removeBOM(h.trim());
            }
            if (h == null || h.isEmpty()) {
                h = "col_" + i;
            }
            // Make duplicates unique
            String base = h;
            int k = 1;
            while (seen.contains(h)) {
                h = base + "_" + (++k);
            }
            seen.add(h);
            out.add(h);
        }
        return out;
    }

    private static String removeBOM(String s) {
        if (s != null && !s.isEmpty() && s.charAt(0) == '\uFEFF') {
            return s.substring(1);
        }
        return s;
    }
}
