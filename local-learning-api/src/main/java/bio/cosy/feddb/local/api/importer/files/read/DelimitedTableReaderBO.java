package bio.cosy.feddb.local.api.importer.files.read;

import bio.cosy.feddb.core.api.file.analytics.ShardedRowProfiler;
import bio.cosy.feddb.local.api.importer.ImportPhaseTimer;
import bio.cosy.feddb.local.api.importer.extract.SheetMergeResultDTO;
import bio.cosy.feddb.local.api.importer.files.progress.ImportTableDTO;
import bio.cosy.feddb.local.api.importer.files.table.ColumnNames;
import bio.cosy.feddb.local.api.importer.files.table.ParsedTables;
import bio.cosy.feddb.local.api.importer.files.table.SheetMergePlan;
import bio.cosy.feddb.local.api.importer.files.table.TableData;
import bio.cosy.feddb.local.config.FLNetClientConfig;
import de.siegmar.fastcsv.reader.CsvReader;
import de.siegmar.fastcsv.reader.CsvRecord;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;


@ApplicationScoped
public class DelimitedTableReaderBO extends TableReaderSupport {


    /** One delimited file, or its tables when the settings ask for all of them. */
    public TableData readFile(File file, TableReadSpec spec) {
        if (spec.allTables()) {
            Map<String, TableData> tables = readArchive(file, spec, null);
            return tables == null ? null : ParsedTables.of(tables).takeFirst();
        }

        String tableName = ColumnNames.tableOfFile(file.getName());
        ImportPhaseTimer timer = ImportPhaseTimer.silent("Read");
        reportTables(spec, List.of(tableName));
        reportTable(spec, ImportTableDTO.reading(tableName, 1, 1));
        try (Reader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8);
             CsvReader<CsvRecord> parser = CsvDialect.reader(reader, spec.delimiter())) {
            TableData table = readTable(parser, spec, sourceName(file), tableName, 1, 1,
                    profileParallelism(1));
            reportTable(spec, ImportTableDTO.read(tableName, 1, 1, table.longSize(),
                    table.getColumns().size(), missingValues(table), timer.elapsedMillis()));
            return table;
        } catch (IOException e) {
            Log.warnf(e, "Could not read CSV file: %s", file.getAbsolutePath());
            reportTable(spec, ImportTableDTO.failed(tableName, 1, 1, e.getMessage(), timer.elapsedMillis()));
            return null;
        }
    }

    /** The archive's tables by name, or the single merged table where a merge is configured. */
    public Map<String, TableData> readArchive(File file, TableReadSpec spec, SheetMergeResultDTO mergeConfig) {
        if (SheetMergePlan.requested(mergeConfig)) {
            TableData merged = mergeArchive(file, spec, mergeConfig);
            return merged == null ? null : Map.of(mergedTableName(), merged);
        }

        List<String> entryNames;
        try (ZipFile zipFile = new ZipFile(file, StandardCharsets.UTF_8)) {
            entryNames = supportedEntryNames(zipFile);
        } catch (IOException e) {
            Log.warnf(e, "Could not read ZIP file: %s", file.getAbsolutePath());
            return null;
        }
        if (entryNames.isEmpty()) {
            return null;
        }

        // Every table the archive holds, before any of them is opened: the list is what the user is
        // shown, and a table that only appeared once it had been read would arrive too late to be
        // worth showing at all.
        reportTables(spec, entryNames.stream().map(ColumnNames::tableOfFile).toList());

        Map<String, TableData> tables = readEntries(file, entryNames, spec);
        return tables.isEmpty() ? null : tables;
    }

    /**
     * Reads the archive's tables, several at a time where the configuration allows it.
     *
     * <p>ZIP entries are independent inputs producing independent tables, so reading them one after
     * another left a multi-table archive bound to a single core for the whole import. Each worker
     * opens its own {@link ZipFile} handle, and results are re-assembled in entry order so the
     * outcome does not depend on how the work was scheduled.</p>
     */
    private Map<String, TableData> readEntries(File file, List<String> entryNames, TableReadSpec spec) {
        int workers = Math.min(entryNames.size(), parseParallelism());
        int profileWorkers = profileParallelism(workers);
        Log.infof("Reading %d ZIP entries with %d workers, %d profiling threads each",
                entryNames.size(), workers, profileWorkers);

        List<Callable<TableData>> tasks = new ArrayList<>(entryNames.size());
        for (int index = 0; index < entryNames.size(); index++) {
            int position = index;
            tasks.add(() -> readEntry(
                    file, entryNames.get(position), spec, position, entryNames.size(), profileWorkers));
        }

        ExecutorService executor = Executors.newFixedThreadPool(workers, Thread.ofPlatform()
                .name("importer-zip-reader", 0)
                .daemon()
                .factory());
        List<Future<TableData>> results = List.of();
        try {
            // invokeAll returns once every entry is read, with the futures in task order.
            results = executor.invokeAll(tasks);
            Map<String, TableData> tables = new LinkedHashMap<>();
            for (int index = 0; index < results.size(); index++) {
                TableData table = results.get(index).get();
                if (table != null) {
                    tables.put(ColumnNames.tableOfFile(entryNames.get(index)), table);
                }
            }
            return tables;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw closing(results, new IllegalStateException("Interrupted while reading ZIP entries", e));
        } catch (ExecutionException e) {
            throw closing(results, e.getCause() instanceof RuntimeException runtime
                    ? runtime
                    : new IllegalStateException(e.getCause()));
        } finally {
            executor.shutdownNow();
        }
    }

    /** Releases the tables that did finish - each holds a temp file - and hands the failure on. */
    private static RuntimeException closing(List<Future<TableData>> results, RuntimeException failure) {
        for (Future<TableData> result : results) {
            try {
                if (result.isDone() && !result.isCancelled() && result.get() != null) {
                    result.get().close();
                }
            } catch (Exception ignored) {
                // Already failing; a table that cannot be released must not hide the reason.
            }
        }
        return failure;
    }

    private TableData readEntry(
            File file,
            String entryName,
            TableReadSpec spec,
            int position,
            int total,
            int profileWorkers
    ) {
        String tableName = ColumnNames.tableOfFile(entryName);
        int place = position + 1;
        String source = sourceName(file, tableName, place, total);
        ImportPhaseTimer timer = ImportPhaseTimer.started("Read", "%s", source);
        reportTable(spec, ImportTableDTO.reading(tableName, place, total));
        try (ZipFile zipFile = new ZipFile(file, StandardCharsets.UTF_8)) {
            ZipEntry entry = zipFile.getEntry(entryName);
            if (entry == null) {
                timer.done("%s - entry is no longer present", source);
                reportTable(spec, ImportTableDTO.skipped(tableName, place, total,
                        "entry is no longer present", timer.elapsedMillis()));
                return null;
            }
            try (CsvReader<CsvRecord> parser = entryReader(zipFile, entry, spec.delimiter())) {
                TableData table = readTable(parser, spec, source, tableName, place, total,
                        profileWorkers);
                if (table.getColumns().size() < 2 || table.longSize() == 0) {
                    table.close();
                    timer.done("%s - skipped, no usable table", source);
                    reportTable(spec, ImportTableDTO.skipped(tableName, place, total,
                            "no usable table", timer.elapsedMillis()));
                    return null;
                }
                timer.done("%s -> %d rows over %d columns",
                        source, table.longSize(), table.getColumns().size());
                reportTable(spec, ImportTableDTO.read(tableName, place, total, table.longSize(),
                        table.getColumns().size(), missingValues(table), timer.elapsedMillis()));
                return table;
            }
        } catch (Exception e) {
            Log.debugf(e, "Skipping ZIP entry %s", entryName);
            timer.failed(e);
            reportTable(spec, ImportTableDTO.failed(tableName, place, total,
                    e.getMessage(), timer.elapsedMillis()));
            return null;
        }
    }

    /**
     * Reads a delimited table, profiling it as it goes where the spec asks for statistics.
     *
     * @param table          the table being read, for whoever is watching the import
     * @param profileWorkers upper bound on profiling threads for this table
     */
    private TableData readTable(
            CsvReader<CsvRecord> parser,
            TableReadSpec spec,
            String source,
            String table,
            int position,
            int total,
            int profileWorkers
    ) {
        Iterator<CsvRecord> records = parser.iterator();
        if (!records.hasNext()) {
            return TableData.empty();
        }

        CsvRecord firstRecord = records.next();
        List<String> columns = CsvDialect.columns(firstRecord, spec.hasHeader());

        // The columns are known from the header alone, well before the rows they describe.
        reportTable(spec, ImportTableDTO.reading(table, position, total, 0L, columns.size(), null));

        // Created only once the columns are known, so the spill file can store rows positionally
        // instead of repeating every column name on every record.
        TableData tableData = createTable(columns, spec);
        try (ShardedRowProfiler profiler = spec.profile()
                ? profiles.rowProfiler(columns, source, profileWorkers,
                        rowReporter(spec, table, position, total, columns))
                : null) {
            if (!spec.hasHeader() && !spec.isFull(0)) {
                appendRow(tableData, columns.size(), firstRecord, spec, profiler);
            }
            long count = tableData.longSize();
            while (records.hasNext() && !spec.isFull(count)) {
                appendRow(tableData, columns.size(), records.next(), spec, profiler);
                count++;
            }
            return profiler == null ? tableData : profiles.attach(tableData, profiler.finish(), source);
        }
    }

    /**
     * Hands one record to the profiler and the spill file as a plain array of values.
     *
     * <p>Both sides want the values in column order, and the spill format stores them that way, so
     * the map this used to build was assembled only to be taken apart again by each of them.</p>
     */
    private void appendRow(
            TableData table,
            int columnCount,
            CsvRecord record,
            TableReadSpec spec,
            ShardedRowProfiler profiler
    ) {
        int fields = record.getFieldCount();
        String[] values = new String[columnCount];
        for (int index = 0; index < columnCount; index++) {
            values[index] = index < fields ? record.getField(index) : null;
        }
        if (profiler != null) {
            profiler.accept(values);
        }
        table.appendRow(values);
        spillIfNeeded(table, spec);
    }

    /**
     * Merges the archive's tables while reading them, straight into one disk-backed table.
     *
     * <p>The general merge takes tables that have already been parsed. Here the merged table is the
     * only one anybody wants, so parsing each entry into a table of its own first would write the
     * whole archive to disk twice.</p>
     */
    private TableData mergeArchive(File file, TableReadSpec spec, SheetMergeResultDTO mergeConfig) {
        try (ZipFile zipFile = new ZipFile(file, StandardCharsets.UTF_8)) {
            Map<String, ArchiveTable> tables = readHeaders(zipFile, spec);
            if (tables.isEmpty()) {
                return null;
            }

            Map<String, List<String>> columnsByTable = new LinkedHashMap<>();
            tables.forEach((name, table) -> columnsByTable.put(name, table.columns()));
            SheetMergePlan plan = SheetMergePlan.of(mergeConfig, columnsByTable);

            Log.infof("Merging %d ZIP CSV tables directly to disk", tables.size());
            TableData merged = TableData.diskBacked(plan.columns(), new ArrayList<>(), workDirectory());
            for (ArchiveTable table : tables.values()) {
                appendArchiveTable(zipFile, table, spec, plan, merged);
            }
            Log.infof("Merged ZIP CSV tables into %d rows and %d columns",
                    merged.longSize(), merged.getColumns().size());

            if (spec.preview()) {
                TableData collapsed = merges.collapse(merged, plan.uidColumn(), spec.maxRows());
                if (collapsed != merged) {
                    merged.close();
                }
                merged = collapsed;
            }
            return spec.profile() ? profiles.enrich(merged, sourceName(file, mergedTableName())) : merged;
        } catch (IOException e) {
            Log.warnf(e, "Could not read ZIP file: %s", file.getAbsolutePath());
            return null;
        }
    }

    private void appendArchiveTable(
            ZipFile zipFile,
            ArchiveTable table,
            TableReadSpec spec,
            SheetMergePlan plan,
            TableData merged
    ) throws IOException {
        SheetMergePlan.RowBuilder builder = plan.rowBuilder(table.name(), table.columns());
        ZipEntry entry = zipFile.getEntry(table.entryName());
        if (builder == null || entry == null) {
            Log.warnf("Skipping ZIP table '%s' during merge: %s",
                    table.name(), builder == null ? "no usable UID column" : "entry is no longer present");
            return;
        }

        long appended = 0L;
        long skipped = 0L;
        try (CsvReader<CsvRecord> parser = entryReader(zipFile, entry, spec.delimiter())) {
            Iterator<CsvRecord> records = parser.iterator();
            if (table.hasHeader() && records.hasNext()) {
                records.next();
            }
            while (records.hasNext()) {
                CsvRecord record = records.next();
                Map<String, Object> row = builder.build(index -> CsvDialect.value(record, index));
                if (row == null) {
                    skipped++;
                    continue;
                }
                merged.appendRow(row);
                appended++;
            }
        }
        Log.infof("Merged ZIP table '%s': appended %d rows, skipped %d rows without a UID",
                table.name(), appended, skipped);
    }

    /** The tables an archive offers, with the columns each of them declares. */
    private Map<String, ArchiveTable> readHeaders(ZipFile zipFile, TableReadSpec spec) {
        Map<String, ArchiveTable> tables = new LinkedHashMap<>();
        for (String entryName : supportedEntryNames(zipFile)) {
            ZipEntry entry = zipFile.getEntry(entryName);
            if (entry == null) {
                continue;
            }
            try (CsvReader<CsvRecord> parser = entryReader(zipFile, entry, spec.delimiter())) {
                Iterator<CsvRecord> records = parser.iterator();
                if (!records.hasNext()) {
                    continue;
                }
                List<String> columns = CsvDialect.columns(records.next(), spec.hasHeader());
                if (columns.size() < 2) {
                    continue;
                }
                String name = ColumnNames.tableOfFile(entryName);
                tables.put(name, new ArchiveTable(entryName, name, columns, spec.hasHeader()));
            } catch (Exception e) {
                Log.debugf(e, "Skipping ZIP entry %s while reading CSV headers", entryName);
            }
        }
        return tables;
    }

    private static CsvReader<CsvRecord> entryReader(ZipFile zipFile, ZipEntry entry, String delimiter)
            throws IOException {
        InputStream in = zipFile.getInputStream(entry);
        return CsvDialect.reader(new InputStreamReader(in, StandardCharsets.UTF_8), delimiter);
    }

    private static List<String> supportedEntryNames(ZipFile zipFile) {
        List<String> names = new ArrayList<>();
        Enumeration<? extends ZipEntry> entries = zipFile.entries();
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            if (!entry.isDirectory() && CsvDialect.isSupportedEntry(entry.getName())) {
                names.add(entry.getName());
            }
        }
        return names;
    }

    /** One delimited table inside an archive, as far as its header row describes it. */
    private record ArchiveTable(String entryName, String name, List<String> columns, boolean hasHeader) {
    }
}
