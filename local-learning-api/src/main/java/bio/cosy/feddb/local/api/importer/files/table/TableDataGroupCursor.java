package bio.cosy.feddb.local.api.importer.files.table;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.LongArrayList;

import java.io.BufferedInputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Streams a grouped row index and returns one patient group at a time. The cursor only materializes
 * the current patient batch in memory.
 */
public final class TableDataGroupCursor implements Closeable {

    private final InputStream indexStream;
    private final FileChannel rowsChannel;
    private final List<String> rowLayout;

    TableDataGroupCursor(Path indexFile, Path rowsFile, List<String> rowLayout) throws IOException {
        this.indexStream = new BufferedInputStream(Files.newInputStream(indexFile));
        this.rowsChannel = FileChannel.open(rowsFile, StandardOpenOption.READ);
        this.rowLayout = rowLayout;
    }

    public TableDataGroup next() throws IOException {
        TableDataGroupIndexDTO index = TableDataCodec.readRecord(indexStream, TableDataGroupIndexDTO.class);
        if (index == null) {
            return null;
        }

        LongArrayList rowOffsets = index.getRowOffsets();
        IntArrayList rowLengths = index.getRowLengths();
        int rowCount = Math.min(rowOffsets.size(), rowLengths.size());

        List<Map<String, Object>> rows = new ArrayList<>(rowCount);
        for (int i = 0; i < rowCount; i++) {
            rows.add(TableDataCodec.readRowAt(
                    rowsChannel, rowOffsets.getLong(i), rowLengths.getInt(i), rowLayout));
        }

        return new TableDataGroup(index.getPatientId(), rows);
    }

    static String groupKey(Map<String, Object> row, String column) {
        return String.valueOf(row == null ? null : row.get(column));
    }

    @Override
    public void close() throws IOException {
        try {
            indexStream.close();
        } finally {
            rowsChannel.close();
        }
    }
}
