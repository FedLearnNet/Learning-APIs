package bio.cosy.feddb.local.api.importer.files.table;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.smile.SmileConstants;
import com.fasterxml.jackson.dataformat.smile.SmileFactory;
import com.fasterxml.jackson.dataformat.smile.SmileGenerator;
import com.fasterxml.jackson.dataformat.smile.SmileParser;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Binary record codec for the disk-backed ETL spill files.
 *
 * <p>Records are written as Smile (Jackson's binary JSON) rather than NDJSON. Each record is framed
 * by a four-byte big-endian payload length, which serves the same purpose newlines did for the text
 * format but without needing to decode characters to find a record boundary: a sequential reader
 * gets each record's offset and length straight from the frame, and the random-access reader used
 * by {@link TableDataGroupCursor} still seeks directly to a stored offset.</p>
 *
 * <p>Rows are written as <em>arrays</em> of values in column order whenever the writer knows the
 * table's column layout. Each record is a standalone Smile document, so Smile's shared-name
 * back-references never span records: written as objects, a table repeats every column name in full
 * on every row, which on a multi-million row file is more bytes of column names than of data. Rows
 * whose keys do not line up with the layout still fall back to objects, and records identify
 * themselves by their leading token, so both shapes can sit in the same file and be read back
 * without out-of-band information.</p>
 *
 * <p>Offsets recorded elsewhere always point at the <em>payload</em>, i.e. just past the frame
 * header.</p>
 */
public final class TableDataCodec {

    /** Size of the length prefix in front of every record. */
    public static final int FRAME_HEADER_BYTES = Integer.BYTES;

    /**
     * Records are standalone Smile documents so they stay independently addressable. The per-record
     * header carries no information in that setup, so it is switched off on both sides.
     */
    private static final SmileFactory FACTORY = SmileFactory.builder()
            .disable(SmileGenerator.Feature.WRITE_HEADER)
            .disable(SmileParser.Feature.REQUIRE_HEADER)
            .build();

    private static final ObjectMapper MAPPER = new ObjectMapper(FACTORY);

    /** Marks a value the row did not contain at all, as opposed to one that was present and null. */
    private static final Object ABSENT = new Object();

    private TableDataCodec() {
    }

    public static byte[] encode(Object value) throws IOException {
        return MAPPER.writeValueAsBytes(value);
    }

    /**
     * Writes one length-framed record.
     *
     * @return the payload length, excluding the frame header
     */
    public static int writeRecord(OutputStream out, Object value) throws IOException {
        byte[] payload = MAPPER.writeValueAsBytes(value);
        writeFrameHeader(out, payload.length);
        out.write(payload);
        return payload.length;
    }

    /**
     * A reusable row encoder for {@code layout}. Pass a {@code null} or empty layout to keep writing
     * rows as objects.
     *
     * <p>Not thread-safe: it holds the scratch buffer that keeps a large spill from allocating one
     * byte array per row.</p>
     */
    public static RowWriter rowWriter(List<String> layout) {
        return new RowWriter(layout);
    }

    /** Reads the next payload, or {@code null} at end of stream. */
    public static byte[] readPayload(InputStream in) throws IOException {
        int length = readFrameHeader(in);
        if (length < 0) {
            return null;
        }
        byte[] payload = in.readNBytes(length);
        if (payload.length != length) {
            throw new IOException("Truncated record: expected " + length + " bytes, got " + payload.length);
        }
        return payload;
    }

    /** Reads and decodes the next record, or {@code null} at end of stream. */
    public static <T> T readRecord(InputStream in, Class<T> type) throws IOException {
        byte[] payload = readPayload(in);
        return payload == null ? null : MAPPER.readValue(payload, type);
    }

    public static Map<String, Object> decodeRow(byte[] payload) throws IOException {
        return MAPPER.readValue(payload, TableData.ROW_TYPE);
    }

    /** Decodes a row written either positionally against {@code layout} or as a plain object. */
    public static Map<String, Object> decodeRow(byte[] payload, List<String> layout) throws IOException {
        if (!isPositional(payload)) {
            return decodeRow(payload);
        }
        List<String> columns = layout == null ? List.of() : layout;
        Map<String, Object> row = new LinkedHashMap<>();
        try (JsonParser parser = FACTORY.createParser(payload)) {
            parser.nextToken();
            int index = 0;
            while (parser.nextToken() != JsonToken.END_ARRAY) {
                Object value = parser.readValueAs(Object.class);
                if (index < columns.size()) {
                    row.put(columns.get(index), value);
                }
                index++;
            }
        }
        return row;
    }

    /** Reads a single row payload from an absolute offset, decoding it against {@code layout}. */
    public static Map<String, Object> readRowAt(
            FileChannel channel,
            long offset,
            int length,
            List<String> layout
    ) throws IOException {
        return decodeRow(readPayloadAt(channel, offset, length), layout);
    }

    private static byte[] readPayloadAt(FileChannel channel, long offset, int length) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(length);
        channel.position(offset);
        while (buffer.hasRemaining()) {
            if (channel.read(buffer) < 0) {
                throw new IOException("Unexpected end of grouped row store");
            }
        }
        return buffer.array();
    }

    /**
     * Pulls a single column out of an encoded row without materialising the whole row.
     *
     * <p>This is what lets the grouping pass read the patient key from every row without paying for
     * a full map per row. The returned string matches {@code String.valueOf(row.get(column))} for
     * the same record, so grouping behaviour is unchanged.</p>
     *
     * @param layout the column order positional records were written in, or {@code null} for objects
     */
    public static String extractColumn(byte[] payload, List<String> layout, String column) throws IOException {
        if (isPositional(payload)) {
            int wantedIndex = layout == null ? -1 : layout.indexOf(column);
            if (wantedIndex < 0) {
                return "null";
            }
            return extractPositional(payload, wantedIndex);
        }

        try (JsonParser parser = FACTORY.createParser(payload)) {
            if (parser.nextToken() != JsonToken.START_OBJECT) {
                return "null";
            }
            while (parser.nextToken() == JsonToken.FIELD_NAME) {
                boolean wanted = column.equals(parser.currentName());
                parser.nextToken();
                if (wanted) {
                    return currentValueAsString(parser);
                }
                parser.skipChildren();
            }
        }
        return "null";
    }

    private static String extractPositional(byte[] payload, int wantedIndex) throws IOException {
        try (JsonParser parser = FACTORY.createParser(payload)) {
            parser.nextToken();
            int index = 0;
            while (parser.nextToken() != JsonToken.END_ARRAY) {
                if (index == wantedIndex) {
                    return currentValueAsString(parser);
                }
                parser.skipChildren();
                index++;
            }
        }
        return "null";
    }

    /**
     * Whether a record was written positionally. Standalone Smile documents start with the token
     * byte of their root value, so the shape is readable without any framing of our own.
     */
    private static boolean isPositional(byte[] payload) {
        return payload.length > 0 && payload[0] == SmileConstants.TOKEN_LITERAL_START_ARRAY;
    }

    /** Mirrors {@code String.valueOf(Object)} over the value Jackson would have boxed into the row map. */
    private static String currentValueAsString(JsonParser parser) throws IOException {
        JsonToken token = parser.currentToken();
        if (token == null) {
            return "null";
        }
        return switch (token) {
            case VALUE_NULL -> "null";
            case VALUE_STRING -> parser.getText();
            case VALUE_NUMBER_INT, VALUE_NUMBER_FLOAT -> String.valueOf(parser.getNumberValue());
            case VALUE_TRUE, VALUE_FALSE -> String.valueOf(parser.getBooleanValue());
            default -> String.valueOf(parser.readValueAs(Object.class));
        };
    }

    private static void writeFrameHeader(OutputStream out, int length) throws IOException {
        byte[] header = new byte[FRAME_HEADER_BYTES];
        putFrameHeader(header, length);
        out.write(header);
    }

    private static void putFrameHeader(byte[] target, int length) {
        ByteBuffer.wrap(target).putInt(length);
    }

    /** @return the payload length, or {@code -1} at a clean end of stream */
    private static int readFrameHeader(InputStream in) throws IOException {
        byte[] header = in.readNBytes(FRAME_HEADER_BYTES);
        if (header.length == 0) {
            return -1;
        }
        if (header.length != FRAME_HEADER_BYTES) {
            throw new IOException("Truncated record frame header");
        }
        return ByteBuffer.wrap(header).getInt();
    }

    /** Encodes rows for one spill file, reusing its scratch buffer across records. */
    public static final class RowWriter {

        private final List<String> layout;
        private final Object[] values;
        // Handed to the stream with writeTo(), so a row never needs a byte[] copy of its own.
        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream(512);
        private final byte[] frame = new byte[FRAME_HEADER_BYTES];

        private RowWriter(List<String> layout) {
            this.layout = layout == null || layout.isEmpty() ? null : List.copyOf(layout);
            this.values = this.layout == null ? null : new Object[this.layout.size()];
        }

        /** The column order rows are written in, or {@code null} when rows are written as objects. */
        public List<String> layout() {
            return layout;
        }

        /**
         * Writes one length-framed row.
         *
         * @return the payload length, excluding the frame header
         */
        public int write(OutputStream out, Map<String, Object> row) throws IOException {
            return writeEncoded(out, positionalValues(row) ? values : row);
        }

        /**
         * Writes one row given positionally, in {@link #layout} order.
         *
         * <p>For a reader that already has the values in order this avoids building a map only for
         * the writer to take it apart again - which, once profiling moved into the parse, was the
         * only reason the map existed at all.</p>
         *
         * @return the payload length, excluding the frame header
         */
        public int write(OutputStream out, Object[] rowValues) throws IOException {
            if (layout == null || rowValues == null || rowValues.length != layout.size()) {
                throw new IllegalArgumentException(
                        "Row values do not match the column layout this writer was created with");
            }
            return writeEncoded(out, rowValues);
        }

        private int writeEncoded(OutputStream out, Object encoded) throws IOException {
            buffer.reset();
            MAPPER.writeValue(buffer, encoded);
            putFrameHeader(frame, buffer.size());
            out.write(frame);
            buffer.writeTo(out);
            return buffer.size();
        }

        /**
         * Fills {@link #values} from {@code row} and reports whether the row is fully described by
         * the layout. A row carrying keys outside the layout is written as an object instead, so a
         * caller that puts more in a row than it declared still round-trips.
         */
        private boolean positionalValues(Map<String, Object> row) {
            if (layout == null || row == null) {
                return false;
            }
            int matched = 0;
            for (int index = 0; index < layout.size(); index++) {
                Object value = row.getOrDefault(layout.get(index), ABSENT);
                if (value == ABSENT) {
                    values[index] = null;
                } else {
                    values[index] = value;
                    matched++;
                }
            }
            return matched == row.size();
        }
    }
}
