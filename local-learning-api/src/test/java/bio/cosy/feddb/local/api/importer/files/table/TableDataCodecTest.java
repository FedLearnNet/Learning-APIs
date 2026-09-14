package bio.cosy.feddb.local.api.importer.files.table;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TableDataCodecTest {

    @Test
    void roundTripsRowValuesThroughTheBinaryFormat() throws IOException {
        Map<String, Object> row = row("pid", "P1", "age", 40, "score", 1.5, "flag", true, "note", null);

        byte[] payload = TableDataCodec.encode(row);

        assertEquals(row, TableDataCodec.decodeRow(payload));
    }

    @Test
    void framesRecordsSoTheyCanBeReadBackSequentially() throws IOException {
        List<Map<String, Object>> written = List.of(
                row("pid", "P1", "age", 40),
                row("pid", "P2", "age", 50),
                row("pid", "P3", "age", 60)
        );

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (Map<String, Object> row : written) {
            TableDataCodec.writeRecord(out, row);
        }

        List<Map<String, Object>> read = new ArrayList<>();
        try (InputStream in = new ByteArrayInputStream(out.toByteArray())) {
            byte[] payload;
            while ((payload = TableDataCodec.readPayload(in)) != null) {
                read.add(TableDataCodec.decodeRow(payload));
            }
        }

        assertEquals(written, read);
    }

    @Test
    void returnsNullPayloadAtEndOfStream() throws IOException {
        try (InputStream in = new ByteArrayInputStream(new byte[0])) {
            assertNull(TableDataCodec.readPayload(in));
        }
    }

    /**
     * The grouping pass reads the patient key with {@code extractColumn} instead of materialising the
     * row, so it has to agree with {@code String.valueOf(row.get(column))} for every value shape the
     * readers can produce — otherwise rows would land in different groups than before.
     */
    @Test
    void extractsColumnExactlyAsAFullRowLookupWould() throws IOException {
        Map<String, Object> row = row(
                "text", "P1",
                "integer", 40,
                "decimal", 1.5,
                "boolean", true,
                "nullValue", null
        );
        byte[] payload = TableDataCodec.encode(row);

        for (String column : List.of("text", "integer", "decimal", "boolean", "nullValue", "absent")) {
            assertEquals(
                    String.valueOf(row.get(column)),
                    TableDataCodec.extractColumn(payload, null, column),
                    "column " + column
            );
        }
    }

    @Test
    void writesRowsPositionallyWhenTheLayoutDescribesThem() throws IOException {
        List<String> layout = List.of("pid", "age", "score");
        Map<String, Object> row = row("pid", "P1", "age", 40, "score", 1.5);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        TableDataCodec.rowWriter(layout).write(out, row);
        ByteArrayOutputStream asObject = new ByteArrayOutputStream();
        TableDataCodec.rowWriter(null).write(asObject, row);

        assertTrue(out.size() < asObject.size(),
                "positional records should be smaller than ones repeating the column names");

        try (InputStream in = new ByteArrayInputStream(out.toByteArray())) {
            assertEquals(row, TableDataCodec.decodeRow(TableDataCodec.readPayload(in), layout));
        }
    }

    /**
     * A record says which shape it is, so a file may hold both. Without that, changing how rows are
     * written would mean every reader needing to be told out of band which era a file came from.
     */
    @Test
    void readsPositionalAndObjectRecordsFromTheSameStream() throws IOException {
        List<String> layout = List.of("pid", "age");
        Map<String, Object> positional = row("pid", "P1", "age", 40);
        // Carries a key the layout does not mention, so it cannot be written positionally.
        Map<String, Object> extra = row("pid", "P2", "age", 50, "unexpected", "x");

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        TableDataCodec.RowWriter writer = TableDataCodec.rowWriter(layout);
        writer.write(out, positional);
        writer.write(out, extra);

        List<Map<String, Object>> read = new ArrayList<>();
        try (InputStream in = new ByteArrayInputStream(out.toByteArray())) {
            byte[] payload;
            while ((payload = TableDataCodec.readPayload(in)) != null) {
                read.add(TableDataCodec.decodeRow(payload, layout));
            }
        }

        assertEquals(List.of(positional, extra), read);
    }

    @Test
    void extractsColumnFromPositionalRecordsExactlyAsAFullRowLookupWould() throws IOException {
        List<String> layout = List.of("text", "integer", "decimal", "boolean", "nullValue");
        Map<String, Object> row = row(
                "text", "P1",
                "integer", 40,
                "decimal", 1.5,
                "boolean", true,
                "nullValue", null
        );

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        TableDataCodec.rowWriter(layout).write(out, row);
        byte[] payload;
        try (InputStream in = new ByteArrayInputStream(out.toByteArray())) {
            payload = TableDataCodec.readPayload(in);
        }

        for (String column : layout) {
            assertEquals(
                    String.valueOf(row.get(column)),
                    TableDataCodec.extractColumn(payload, layout, column),
                    "column " + column
            );
        }
        assertEquals("null", TableDataCodec.extractColumn(payload, layout, "absent"));
    }

    /** A row missing a layout column keeps decoding, with that column reading as absent. */
    @Test
    void keepsMissingLayoutColumnsReadableAsNull() throws IOException {
        List<String> layout = List.of("pid", "age");
        Map<String, Object> row = row("pid", "P1");

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        TableDataCodec.rowWriter(layout).write(out, row);

        try (InputStream in = new ByteArrayInputStream(out.toByteArray())) {
            Map<String, Object> decoded = TableDataCodec.decodeRow(TableDataCodec.readPayload(in), layout);
            assertEquals("P1", decoded.get("pid"));
            assertNull(decoded.get("age"));
        }
    }

    private static Map<String, Object> row(Object... keysAndValues) {
        Map<String, Object> row = new LinkedHashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            row.put((String) keysAndValues[i], keysAndValues[i + 1]);
        }
        return row;
    }
}
