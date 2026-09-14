package bio.cosy.feddb.local.api.importer.files.table;


import bio.cosy.feddb.core.api.file.analytics.ColumnProfile;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.logging.Log;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

@Getter
@Setter
@NoArgsConstructor
public class TableData implements AutoCloseable {
    static final TypeReference<LinkedHashMap<String, Object>> ROW_TYPE = new TypeReference<>() {
    };

    private List<String> columns = new ArrayList<>();
    private List<Map<String, Object>> rows = new ArrayList<>();
    private List<ColumnProfile> columnProfiles;

    /** Spill buffer size. The default 8 KB turns a multi-GB spill into a syscall storm. */
    private static final int SPILL_BUFFER_BYTES = 512 * 1024;

    @JsonIgnore
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private transient Path rowsFile;
    @JsonIgnore
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private transient OutputStream rowsWriter;
    @JsonIgnore
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private transient long diskRowCount;
    /**
     * Column order the spill file was written in, captured when the file was opened. Kept separate
     * from {@link #columns} because a caller may rename or re-order columns afterwards, and the
     * records already on disk have to keep decoding against the order they were written with.
     */
    @JsonIgnore
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private transient TableDataCodec.RowWriter rowCodec;

    public TableData(List<String> columns, List<Map<String, Object>> rows, List<ColumnProfile> columnProfiles) {
        this.columns = columns == null ? new ArrayList<>() : columns;
        this.rows = rows == null ? new ArrayList<>() : rows;
        this.columnProfiles = columnProfiles;
    }

    public long longSize() {
        return isDiskBacked() ? diskRowCount : rows == null ? 0 : rows.size();
    }

    @JsonIgnore
    public boolean isDiskBacked() {
        return rowsFile != null;
    }

    public TableData limit(int maxRows) {
        if (maxRows < 0 || longSize() <= maxRows) {
            return this;
        }
        List<Map<String, Object>> limitedRows = new ArrayList<>(maxRows);
        try (Stream<Map<String, Object>> stream = streamRows()) {
            stream.limit(maxRows).forEach(limitedRows::add);
        }
        return new TableData(columns, limitedRows, columnProfiles);
    }

    public static TableData empty() {
        return new TableData(new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
    }

    public static TableData diskBacked(List<String> columns, List<ColumnProfile> columnProfiles, Path baseDir) {
        TableData tableData = new TableData(columns, new ArrayList<>(), columnProfiles);
        tableData.spillRowsToTempFile(baseDir);
        return tableData;
    }

    public static Map<String, Object> readRow(ObjectMapper objectMapper, JsonParser parser) throws IOException {
        return objectMapper.readValue(parser, ROW_TYPE);
    }

    /** Column order the spill file is written in, or {@code null} when rows are stored as objects. */
    @JsonIgnore
    public List<String> rowLayout() {
        return rowCodec == null ? null : rowCodec.layout();
    }

    public void appendRow(Map<String, Object> row) {
        if (isDiskBacked()) {
            try {
                rowCodec.write(rowsWriter, row);
                diskRowCount++;
            } catch (IOException e) {
                throw new UncheckedIOException("Could not append row to disk-backed table data", e);
            }
        } else {
            rows.add(row);
        }
    }

    /**
     * Appends a row given positionally, in this table's column order.
     *
     * <p>The spill format stores rows positionally anyway, so a reader that already has the values
     * in order can hand them straight over. Building a map first only to have the writer read it
     * back out is the kind of work that does not show up until there are tens of millions of rows.</p>
     */
    public void appendRow(Object[] values) {
        if (isDiskBacked() && rowCodec.layout() != null && values != null
                && values.length == rowCodec.layout().size()) {
            try {
                rowCodec.write(rowsWriter, values);
                diskRowCount++;
            } catch (IOException e) {
                throw new UncheckedIOException("Could not append row to disk-backed table data", e);
            }
            return;
        }
        // In memory, or a layout that does not line up: fall back to the map form, which is also
        // what the in-memory row list has to hold.
        appendRow(toRow(values));
    }

    private Map<String, Object> toRow(Object[] values) {
        Map<String, Object> row = new LinkedHashMap<>();
        if (values == null) {
            return row;
        }
        for (int index = 0; index < values.length && index < columns.size(); index++) {
            row.put(columns.get(index), values[index]);
        }
        return row;
    }

    public void spillRowsToTempFile(Path baseDir) {
        if (isDiskBacked()) {
            return;
        }
        try {
            rowsFile = createRowsFile(baseDir);
            rowsFile.toFile().deleteOnExit();
            rowsWriter = new BufferedOutputStream(Files.newOutputStream(rowsFile), SPILL_BUFFER_BYTES);
            rowCodec = TableDataCodec.rowWriter(columns);
            List<Map<String, Object>> currentRows = rows == null ? List.of() : rows;
            for (Map<String, Object> row : currentRows) {
                rowCodec.write(rowsWriter, row);
            }
            diskRowCount = currentRows.size();
            rows = new ArrayList<>();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not spill table data rows to disk", e);
        }
    }

    Path detachRowsFile(Path targetFile) throws IOException {
        if (!isDiskBacked()) {
            return null;
        }
        flushRowsWriter();
        if (rowsWriter != null) {
            rowsWriter.close();
            rowsWriter = null;
        }
        if (targetFile.getParent() != null) {
            Files.createDirectories(targetFile.getParent());
        }
        Files.move(rowsFile, targetFile, StandardCopyOption.REPLACE_EXISTING);
        rowsFile = null;
        rowCodec = null;
        diskRowCount = 0L;
        rows = new ArrayList<>();
        return targetFile;
    }

    /**
     * The rows, read back from the spill file where they were written to one.
     *
     * <p>The stream owns the open file, so callers have to close it - which is why every caller reads
     * it inside a try-with-resources.</p>
     */
    @JsonIgnore
    public Stream<Map<String, Object>> streamRows() {
        if (!isDiskBacked()) {
            return (rows == null ? List.<Map<String, Object>>of() : rows).stream();
        }

        try {
            flushRowsWriter();
            List<String> layout = rowLayout();
            InputStream in = new BufferedInputStream(Files.newInputStream(rowsFile), SPILL_BUFFER_BYTES);
            return Stream.generate(() -> readRow(in, layout))
                    .takeWhile(Objects::nonNull)
                    .onClose(() -> close(in));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not open disk-backed table rows", e);
        }
    }

    /** The next spilled row, or {@code null} at the end of the file. */
    private static Map<String, Object> readRow(InputStream in, List<String> layout) {
        try {
            byte[] payload = TableDataCodec.readPayload(in);
            return payload == null ? null : TableDataCodec.decodeRow(payload, layout);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read disk-backed table rows", e);
        }
    }

    private static void close(InputStream in) {
        try {
            in.close();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not close disk-backed table reader", e);
        }
    }

    public List<Map<String, Object>> getRows() {
        if (!isDiskBacked()) {
            return rows;
        }
        try (Stream<Map<String, Object>> stream = streamRows()) {
            return new ArrayList<>(stream.toList());
        }
    }

    @Override
    public void close() {
        try {
            flushRowsWriter();
            if (rowsWriter != null) {
                rowsWriter.close();
                rowsWriter = null;
            }
            if (rowsFile != null) {
                Files.deleteIfExists(rowsFile);
                rowsFile = null;
                rowCodec = null;
                diskRowCount = 0L;
            }
        } catch (IOException e) {
            Log.debugf(e, "Could not close disk-backed table data");
        }
    }

    private void flushRowsWriter() throws IOException {
        if (rowsWriter != null) {
            rowsWriter.flush();
        }
    }

    private Path createRowsFile(Path baseDir) throws IOException {
        if (baseDir == null) {
            return Files.createTempFile("importer-table-data-", ".smile");
        }
        Files.createDirectories(baseDir);
        return Files.createTempFile(baseDir, "importer-table-data-", ".smile");
    }
}
