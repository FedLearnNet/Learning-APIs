package bio.cosy.feddb.local.api.importer.files.table;

import bio.cosy.feddb.local.config.FLNetClientConfig;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Disk-backed table operations used by streaming imports. It builds a hash-partitioned row index so
 * the ETL can drain one patient group at a time without externally sorting the whole input.
 */
@ApplicationScoped
public class TableDataGroupingBO {

    private static final int MAX_BUCKETS = 128;

    /** Row-store buffer size; the default 8 KB turns a multi-GB pass into a syscall storm. */
    private static final int ROW_BUFFER_BYTES = 512 * 1024;

    @Inject
    FLNetClientConfig config;

    public TableDataGroups groupByColumn(TableData data, String column, int targetRowsPerBucket) throws IOException {
        if (data == null) {
            throw new IllegalArgumentException("data must not be null");
        }
        if (column == null || column.isBlank()) {
            throw new IllegalArgumentException("column must not be blank");
        }

        Path workDir = createWorkDir();
        try {
            int bucketCount = bucketCount(data.longSize(), targetRowsPerBucket);
            List<Path> bucketFiles = createBucketFiles(workDir, bucketCount);
            Path rowsFile = workDir.resolve("rows.smile");
            // The rows keep whatever encoding they already have on disk, so the group store has to
            // carry the same column order the spill file was written with.
            List<String> layout;
            if (data.isDiskBacked()) {
                layout = data.rowLayout();
                rowsFile = data.detachRowsFile(rowsFile);
                writeReferences(rowsFile, column, layout, bucketFiles);
            } else {
                layout = writeRowsAndReferences(data, column, rowsFile, bucketFiles);
            }

            Path indexFile = workDir.resolve("groups.smile");
            long groupCount = writeGroupedIndex(bucketFiles, indexFile);
            return new TableDataGroups(workDir, indexFile, rowsFile, groupCount, layout);
        } catch (IOException | RuntimeException e) {
            TableDataTempFiles.deleteRecursively(workDir);
            throw e;
        }
    }

    /**
     * Kept for existing callers. The implementation no longer sorts; it builds a disk-backed group
     * index and exposes the same cursor contract.
     */
    @Deprecated
    public TableDataGroups sortByColumn(TableData data, String column, int chunkRows) throws IOException {
        return groupByColumn(data, column, chunkRows);
    }

    private int bucketCount(long rowCount, int targetRowsPerBucket) {
        int targetRows = Math.max(1, targetRowsPerBucket);
        long buckets = Math.max(1L, (rowCount + targetRows - 1L) / targetRows);
        return Math.toIntExact(Math.min(MAX_BUCKETS, buckets));
    }

    private List<Path> createBucketFiles(Path workDir, int bucketCount) {
        List<Path> bucketFiles = new ArrayList<>(bucketCount);
        for (int i = 0; i < bucketCount; i++) {
            bucketFiles.add(workDir.resolve("refs-" + i + ".smile"));
        }
        return bucketFiles;
    }

    /** @return the column order the rows were written in */
    private List<String> writeRowsAndReferences(
            TableData data,
            String column,
            Path rowsFile,
            List<Path> bucketFiles
    ) throws IOException {
        TableDataCodec.RowWriter rowCodec = TableDataCodec.rowWriter(data.getColumns());
        List<OutputStream> bucketWriters = openBucketWriters(bucketFiles);
        try (OutputStream rowsOut = new BufferedOutputStream(Files.newOutputStream(rowsFile), ROW_BUFFER_BYTES);
             var rows = data.streamRows()) {
            var iterator = rows.iterator();
            long rowIdx = 0L;
            long framePos = 0L;
            while (iterator.hasNext()) {
                var row = iterator.next();
                String patientId = TableDataGroupCursor.groupKey(row, column);
                int rowLength = rowCodec.write(rowsOut, row);

                writeReference(bucketWriters, bucketFiles.size(), patientId, rowIdx,
                        framePos + TableDataCodec.FRAME_HEADER_BYTES, rowLength);

                rowIdx++;
                framePos += TableDataCodec.FRAME_HEADER_BYTES + rowLength;
            }
        } finally {
            closeBucketWriters(bucketWriters);
        }
        return rowCodec.layout();
    }

    /**
     * Builds the bucket references for rows that are already spilled to disk.
     *
     * <p>Row offsets and lengths come straight from the record framing, and only the grouping column
     * is pulled out of each record. Previously this pass decoded every row into a map purely to read
     * one field, and re-encoded each line to measure its byte length — so a full copy of the input
     * went through the parser and the allocator for no other reason.</p>
     */
    private void writeReferences(Path rowsFile, String column, List<String> layout, List<Path> bucketFiles)
            throws IOException {
        List<OutputStream> bucketWriters = openBucketWriters(bucketFiles);
        try (InputStream rowsIn = new BufferedInputStream(Files.newInputStream(rowsFile), ROW_BUFFER_BYTES)) {
            long rowIdx = 0L;
            long framePos = 0L;
            byte[] payload;
            while ((payload = TableDataCodec.readPayload(rowsIn)) != null) {
                String patientId = TableDataCodec.extractColumn(payload, layout, column);

                writeReference(bucketWriters, bucketFiles.size(), patientId, rowIdx,
                        framePos + TableDataCodec.FRAME_HEADER_BYTES, payload.length);

                rowIdx++;
                framePos += TableDataCodec.FRAME_HEADER_BYTES + payload.length;
            }
        } finally {
            closeBucketWriters(bucketWriters);
        }
    }

    private void writeReference(
            List<OutputStream> bucketWriters,
            int bucketCount,
            String patientId,
            long rowIdx,
            long rowOffset,
            int rowLength
    ) throws IOException {
        TableDataRowReferenceDTO reference =
                new TableDataRowReferenceDTO(patientId, rowIdx, rowOffset, rowLength);
        TableDataCodec.writeRecord(bucketWriters.get(bucket(patientId, bucketCount)), reference);
    }

    private List<OutputStream> openBucketWriters(List<Path> bucketFiles) throws IOException {
        List<OutputStream> writers = new ArrayList<>(bucketFiles.size());
        try {
            for (Path bucketFile : bucketFiles) {
                writers.add(new BufferedOutputStream(Files.newOutputStream(bucketFile)));
            }
            return writers;
        } catch (IOException e) {
            closeBucketWriters(writers);
            throw e;
        }
    }

    private void closeBucketWriters(List<OutputStream> writers) throws IOException {
        IOException thrown = null;
        for (OutputStream writer : writers) {
            try {
                writer.close();
            } catch (IOException e) {
                if (thrown == null) {
                    thrown = e;
                } else {
                    thrown.addSuppressed(e);
                }
            }
        }
        if (thrown != null) {
            throw thrown;
        }
    }

    private long writeGroupedIndex(List<Path> bucketFiles, Path indexFile) throws IOException {
        long groupCount = 0L;
        try (OutputStream writer = new BufferedOutputStream(Files.newOutputStream(indexFile))) {
            for (Path bucketFile : bucketFiles) {
                Map<String, GroupIndexBuilder> grouped = readBucket(bucketFile);
                for (GroupIndexBuilder builder : grouped.values()) {
                    TableDataCodec.writeRecord(writer, builder.toIndex());
                }
                groupCount += grouped.size();
            }
        }
        return groupCount;
    }

    private Map<String, GroupIndexBuilder> readBucket(Path bucketFile) throws IOException {
        Map<String, GroupIndexBuilder> grouped = new LinkedHashMap<>();
        try (InputStream in = new BufferedInputStream(Files.newInputStream(bucketFile))) {
            TableDataRowReferenceDTO reference;
            while ((reference = TableDataCodec.readRecord(in, TableDataRowReferenceDTO.class)) != null) {
                grouped.computeIfAbsent(reference.getPatientId(), GroupIndexBuilder::new)
                        .add(reference);
            }
        }
        return grouped;
    }

    private int bucket(String key, int bucketCount) {
        return Math.floorMod(key == null ? 0 : key.hashCode(), bucketCount);
    }

    private Path createWorkDir() throws IOException {
        return TableDataTempFiles.createWorkDirectory(config, "etl-table-");
    }

    private static final class GroupIndexBuilder {
        private final String patientId;
        private final LongArrayList rowIdx = new LongArrayList();
        private final LongArrayList rowOffsets = new LongArrayList();
        private final IntArrayList rowLengths = new IntArrayList();

        private GroupIndexBuilder(String patientId) {
            this.patientId = patientId;
        }

        private void add(TableDataRowReferenceDTO reference) {
            rowIdx.add(reference.getRowIdx());
            rowOffsets.add(reference.getRowOffset());
            rowLengths.add(reference.getRowLength());
        }

        private TableDataGroupIndexDTO toIndex() {
            return new TableDataGroupIndexDTO(patientId, rowIdx, rowOffsets, rowLengths);
        }
    }
}
