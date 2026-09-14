package bio.cosy.feddb.local.api.importer.files.table;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/** Disk-backed row groups for a {@link TableData}. */
public final class TableDataGroups implements Closeable {

    private final Path workDir;
    private final Path indexFile;
    private final Path rowsFile;
    private final long groupCount;
    private final List<String> rowLayout;

    TableDataGroups(Path workDir, Path indexFile, Path rowsFile, long groupCount, List<String> rowLayout) {
        this.workDir = workDir;
        this.indexFile = indexFile;
        this.rowsFile = rowsFile;
        this.groupCount = groupCount;
        this.rowLayout = rowLayout;
    }

    public long groupCount() {
        return groupCount;
    }

    public TableDataGroupCursor cursor() throws IOException {
        return new TableDataGroupCursor(indexFile, rowsFile, rowLayout);
    }

    @Override
    public void close() {
        TableDataTempFiles.deleteRecursively(workDir);
    }
}
