package bio.cosy.feddb.local.api.importer.files.table;

import bio.cosy.feddb.local.api.importer.extract.PivotConfigDTO;
import bio.cosy.feddb.local.api.importer.extract.PivotMode;
import bio.cosy.feddb.local.api.importer.extract.PivotValueFormat;
import bio.cosy.feddb.local.config.FLNetClientConfig;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

@ApplicationScoped
public class TableDataPivotBO {

    private static final int PIVOT_BATCH_SIZE = 1_024;
    private static final int MAX_OPEN_BATCH_WRITERS = 32;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<List<Object>> VALUE_LIST_TYPE = new TypeReference<>() {
    };

    @Inject
    FLNetClientConfig config;

    public TableData pivot(PivotConfigDTO pivotConfig, TableData tableData) {
        if (tableData == null || !hasPivotConfig(pivotConfig)) {
            return tableData;
        }

        String configKey = firstConfiguredTableKey(pivotConfig);
        Integer valueColumnIndex = pivotConfig.getValueColumnIndex().get(configKey);
        return pivotConfigured(
                valueColumnIndex,
                resolvePivotMode(pivotConfig, configKey),
                resolvePrefix(pivotConfig, configKey),
                resolveValueFormat(pivotConfig, configKey),
                tableData,
                null
        );
    }

    public TableData pivot(int valueColumnIndex, TableData tableData) {
        return pivotTranspose(valueColumnIndex, tableData, null);
    }

    private TableData pivotConfigured(
            int valueColumnIndex,
            PivotMode mode,
            String prefix,
            PivotValueFormat valueFormat,
            TableData tableData,
            Integer maxOutputRows
    ) {
        return mode == PivotMode.ONE_HOT
                ? pivotOneHot(valueColumnIndex, prefix, valueFormat, tableData, maxOutputRows)
                : pivotTranspose(valueColumnIndex, tableData, maxOutputRows);
    }

    private TableData pivotTranspose(int valueColumnIndex, TableData tableData, Integer maxOutputRows) {
        if (tableData == null) {
            return null;
        }

        List<String> sourceColumns = tableData.getColumns() == null
                ? List.of()
                : new ArrayList<>(tableData.getColumns());
        if (valueColumnIndex < 0 || valueColumnIndex >= sourceColumns.size()) {
            throw new BadRequestException(
                    "Pivot value column index %d is outside the available column range 0..%d"
                            .formatted(valueColumnIndex, Math.max(0, sourceColumns.size() - 1))
            );
        }

        String valueColumn = sourceColumns.get(valueColumnIndex);
        if (valueColumn == null || valueColumn.isBlank()) {
            throw new BadRequestException("Pivot value column must have a non-blank name");
        }

        List<String> pivotedColumns = readPivotedColumns(tableData, valueColumn);
        TableData pivoted = TableData.diskBacked(pivotedColumns, new ArrayList<>(), TableDataTempFiles.baseDirectory(config));

        try {
            List<String> outputRowNames = new ArrayList<>();
            for (int sourceColumnIndex = 0; sourceColumnIndex < sourceColumns.size(); sourceColumnIndex++) {
                if (sourceColumnIndex == valueColumnIndex) {
                    continue;
                }
                if (maxOutputRows != null && maxOutputRows >= 0 && outputRowNames.size() >= maxOutputRows) {
                    break;
                }
                outputRowNames.add(sourceColumns.get(sourceColumnIndex));
            }

            writePivotedRows(tableData, valueColumn, pivotedColumns, outputRowNames, pivoted);
            return pivoted;
        } catch (RuntimeException e) {
            pivoted.close();
            throw e;
        }
    }

    private TableData pivotOneHot(
            int valueColumnIndex,
            String prefix,
            PivotValueFormat valueFormat,
            TableData tableData,
            Integer maxOutputRows
    ) {
        if (tableData == null) {
            return null;
        }

        List<String> sourceColumns = tableData.getColumns() == null
                ? List.of()
                : new ArrayList<>(tableData.getColumns());
        validateColumnIndex(valueColumnIndex, sourceColumns, "Pivot value");

        String valueColumn = sourceColumns.get(valueColumnIndex);
        List<String> retainedColumns = new ArrayList<>(sourceColumns);
        retainedColumns.remove(valueColumnIndex);
        Set<String> generatedColumns = new LinkedHashSet<>();

        long rowNumber = 0L;
        try (Stream<Map<String, Object>> rows = tableData.streamRows()) {
            Iterator<Map<String, Object>> iterator = rows.iterator();
            while (iterator.hasNext()) {
                rowNumber++;
                Map<String, Object> sourceRow = iterator.next();
                String generatedColumn = prefix + getGeneratedColumnName(sourceRow, valueColumn, rowNumber);
                if (retainedColumns.contains(generatedColumn)) {
                    throw new BadRequestException(
                            "Pivot value '%s' conflicts with a retained source column".formatted(generatedColumn)
                    );
                }
                generatedColumns.add(generatedColumn);
            }
        }

        List<String> outputColumns = new ArrayList<>(retainedColumns);
        outputColumns.addAll(generatedColumns);
        TableData pivoted = TableData.diskBacked(outputColumns, new ArrayList<>(), TableDataTempFiles.baseDirectory(config));
        try (Stream<Map<String, Object>> rows = tableData.streamRows()) {
            Iterator<Map<String, Object>> iterator = rows.iterator();
            int outputRowCount = 0;
            while (iterator.hasNext()) {
                if (maxOutputRows != null && maxOutputRows >= 0 && outputRowCount >= maxOutputRows) {
                    break;
                }
                Map<String, Object> sourceRow = iterator.next();
                Map<String, Object> outputRow = new LinkedHashMap<>();
                for (String retainedColumn : retainedColumns) {
                    outputRow.put(retainedColumn, sourceRow == null ? null : sourceRow.get(retainedColumn));
                }
                String activeColumn = prefix + getGeneratedColumnName(
                        sourceRow,
                        valueColumn,
                        outputRowCount + 1L
                );
                for (String generatedColumn : generatedColumns) {
                    outputRow.put(
                            generatedColumn,
                            formatPivotValue(generatedColumn.equals(activeColumn), valueFormat)
                    );
                }
                pivoted.appendRow(outputRow);
                outputRowCount++;
            }
            return pivoted;
        } catch (RuntimeException e) {
            pivoted.close();
            throw e;
        }
    }

    private void validateColumnIndex(int columnIndex, List<String> sourceColumns, String label) {
        if (columnIndex < 0 || columnIndex >= sourceColumns.size()) {
            throw new BadRequestException(
                    "%s column index %d is outside the available column range 0..%d"
                            .formatted(label, columnIndex, Math.max(0, sourceColumns.size() - 1))
            );
        }
    }

    private String getGeneratedColumnName(Map<String, Object> sourceRow, String valueColumn, long rowNumber) {
        Object rawColumnName = sourceRow == null ? null : sourceRow.get(valueColumn);
        String columnName = rawColumnName == null ? "" : String.valueOf(rawColumnName).trim();
        if (columnName.isBlank()) {
            throw new BadRequestException(
                    "Pivot value column '%s' is blank in source row %d".formatted(valueColumn, rowNumber)
            );
        }
        return columnName;
    }

    private Object formatPivotValue(boolean value, PivotValueFormat valueFormat) {
        return switch (valueFormat) {
            case YES_NO -> value ? "yes" : "no";
            case ONE_ZERO -> value ? 1 : 0;
            case TRUE_FALSE -> value;
        };
    }

    private void writePivotedRows(
            TableData source,
            String valueColumn,
            List<String> pivotedColumns,
            List<String> outputRowNames,
            TableData pivoted
    ) {
        if (outputRowNames.isEmpty()) {
            return;
        }

        Path batchDirectory = createBatchDirectory();
        int batchCount = (outputRowNames.size() + PIVOT_BATCH_SIZE - 1) / PIVOT_BATCH_SIZE;
        Map<Integer, BufferedWriter> writers = new LinkedHashMap<>(16, 0.75f, true);
        try {
            spoolSourceRows(source, outputRowNames, batchDirectory, batchCount, writers);
            closeWriters(writers);
            emitPivotedBatches(valueColumn, pivotedColumns, outputRowNames, batchDirectory, batchCount, pivoted);
        } finally {
            closeWriters(writers);
            TableDataTempFiles.deleteRecursively(batchDirectory);
        }
    }

    private void spoolSourceRows(
            TableData source,
            List<String> outputRowNames,
            Path batchDirectory,
            int batchCount,
            Map<Integer, BufferedWriter> writers
    ) {
        try (Stream<Map<String, Object>> rows = source.streamRows()) {
            Iterator<Map<String, Object>> iterator = rows.iterator();
            while (iterator.hasNext()) {
                Map<String, Object> sourceRow = iterator.next();
                if (sourceRow == null) {
                    continue;
                }

                for (int batchIndex = 0; batchIndex < batchCount; batchIndex++) {
                    int fromIndex = batchIndex * PIVOT_BATCH_SIZE;
                    int toIndex = Math.min(outputRowNames.size(), fromIndex + PIVOT_BATCH_SIZE);
                    List<Object> values = new ArrayList<>(toIndex - fromIndex);
                    for (int i = fromIndex; i < toIndex; i++) {
                        values.add(sourceRow.get(outputRowNames.get(i)));
                    }

                    BufferedWriter writer = getBatchWriter(batchDirectory, batchIndex, writers);
                    writer.write(OBJECT_MAPPER.writeValueAsString(values));
                    writer.newLine();
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Could not spool pivot source rows", e);
        }
    }

    private void emitPivotedBatches(
            String valueColumn,
            List<String> pivotedColumns,
            List<String> outputRowNames,
            Path batchDirectory,
            int batchCount,
            TableData pivoted
    ) {
        for (int batchIndex = 0; batchIndex < batchCount; batchIndex++) {
            int fromIndex = batchIndex * PIVOT_BATCH_SIZE;
            int toIndex = Math.min(outputRowNames.size(), fromIndex + PIVOT_BATCH_SIZE);
            List<Map<String, Object>> batchRows = new ArrayList<>(toIndex - fromIndex);
            for (int i = fromIndex; i < toIndex; i++) {
                Map<String, Object> outputRow = new LinkedHashMap<>();
                outputRow.put(valueColumn, outputRowNames.get(i));
                batchRows.add(outputRow);
            }

            Path batchFile = batchFile(batchDirectory, batchIndex);
            try (BufferedReader reader = Files.newBufferedReader(batchFile, StandardCharsets.UTF_8)) {
                String line;
                int pivotedColumnIndex = 1;
                while ((line = reader.readLine()) != null) {
                    if (pivotedColumnIndex >= pivotedColumns.size()) {
                        throw new BadRequestException("Pivot source changed while it was being processed");
                    }
                    List<Object> values = OBJECT_MAPPER.readValue(line, VALUE_LIST_TYPE);
                    String pivotedColumn = pivotedColumns.get(pivotedColumnIndex++);
                    for (int i = 0; i < batchRows.size(); i++) {
                        batchRows.get(i).put(pivotedColumn, i < values.size() ? values.get(i) : null);
                    }
                }
                if (pivotedColumnIndex != pivotedColumns.size()) {
                    throw new BadRequestException("Pivot source changed while it was being processed");
                }
            } catch (IOException e) {
                throw new UncheckedIOException("Could not read pivot batch " + batchIndex, e);
            }

            batchRows.forEach(pivoted::appendRow);
        }
    }

    private BufferedWriter getBatchWriter(
            Path batchDirectory,
            int batchIndex,
            Map<Integer, BufferedWriter> writers
    ) throws IOException {
        BufferedWriter existing = writers.get(batchIndex);
        if (existing != null) {
            return existing;
        }
        if (writers.size() >= MAX_OPEN_BATCH_WRITERS) {
            Iterator<Map.Entry<Integer, BufferedWriter>> iterator = writers.entrySet().iterator();
            Map.Entry<Integer, BufferedWriter> eldest = iterator.next();
            eldest.getValue().close();
            iterator.remove();
        }

        BufferedWriter writer = Files.newBufferedWriter(
                batchFile(batchDirectory, batchIndex),
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND
        );
        writers.put(batchIndex, writer);
        return writer;
    }

    private void closeWriters(Map<Integer, BufferedWriter> writers) {
        UncheckedIOException failure = null;
        for (BufferedWriter writer : writers.values()) {
            try {
                writer.close();
            } catch (IOException e) {
                if (failure == null) {
                    failure = new UncheckedIOException("Could not close pivot batch writer", e);
                }
            }
        }
        writers.clear();
        if (failure != null) {
            throw failure;
        }
    }

    private Path createBatchDirectory() {
        try {
            return TableDataTempFiles.createWorkDirectory(config, "importer-table-pivot-");
        } catch (IOException e) {
            throw new UncheckedIOException("Could not create pivot work directory", e);
        }
    }

    private Path batchFile(Path batchDirectory, int batchIndex) {
        return batchDirectory.resolve("batch-%08d.ndjson".formatted(batchIndex));
    }

    /**
     * Pivots every configured table in a parsed multi-table result. Replaced
     * source tables are closed as soon as their disk-backed output is ready.
     */
    public ParsedTables pivotTables(PivotConfigDTO pivotConfig, ParsedTables parsedTables) {
        return pivotTables(pivotConfig, parsedTables, null);
    }

    public ParsedTables pivotTables(
            PivotConfigDTO pivotConfig,
            ParsedTables parsedTables,
            Integer maxOutputRows
    ) {
        if (parsedTables == null || !hasPivotConfig(pivotConfig)) {
            return parsedTables;
        }

        Map<String, TableData> pivotedTables = new LinkedHashMap<>();
        Map<String, TableData> tables = parsedTables.tables();
        String singleTableConfigKey = tables.size() == 1 ? firstConfiguredTableKey(pivotConfig) : null;
        try {
            for (Map.Entry<String, TableData> entry : tables.entrySet()) {
                String tableName = entry.getKey();
                TableData tableData = entry.getValue();
                if (tableData == null) {
                    continue;
                }

                String configKey = singleTableConfigKey != null ? singleTableConfigKey : entry.getKey();
                Integer valueColumnIndex = singleTableConfigKey != null
                        ? pivotConfig.getValueColumnIndex().get(singleTableConfigKey)
                        : resolveValueColumnIndex(pivotConfig, tableName);
                if (valueColumnIndex == null) {
                    pivotedTables.put(tableName, tableData);
                    continue;
                }

                TableData pivoted = pivotConfigured(
                        valueColumnIndex,
                        resolvePivotMode(pivotConfig, configKey),
                        resolvePrefix(pivotConfig, configKey),
                        resolveValueFormat(pivotConfig, configKey),
                        tableData,
                        maxOutputRows
                );
                tableData.close();
                pivotedTables.put(tableName, pivoted);
            }
            tables.clear();
            return ParsedTables.of(pivotedTables);
        } catch (RuntimeException e) {
            pivotedTables.values().forEach(TableData::close);
            parsedTables.close();
            throw e;
        }
    }

    private List<String> readPivotedColumns(TableData tableData, String valueColumn) {
        List<String> columns = new ArrayList<>();
        columns.add(valueColumn);
        Set<String> knownColumns = new LinkedHashSet<>();
        knownColumns.add(valueColumn);

        long rowNumber = 0L;
        try (Stream<Map<String, Object>> rows = tableData.streamRows()) {
            Iterator<Map<String, Object>> iterator = rows.iterator();
            while (iterator.hasNext()) {
                rowNumber++;
                Map<String, Object> row = iterator.next();
                Object rawColumnName = row == null ? null : row.get(valueColumn);
                String columnName = rawColumnName == null ? "" : String.valueOf(rawColumnName).trim();
                if (columnName.isBlank()) {
                    throw new BadRequestException(
                            "Pivot value column '%s' is blank in source row %d".formatted(valueColumn, rowNumber)
                    );
                }
                if (!knownColumns.add(columnName)) {
                    throw new BadRequestException(
                            "Pivot value column '%s' produces duplicate output column '%s'"
                                    .formatted(valueColumn, columnName)
                    );
                }
                columns.add(columnName);
            }
        }
        return columns;
    }

    private Integer resolveValueColumnIndex(PivotConfigDTO pivotConfig, String tableName) {
        return ColumnNames.configuredFor(pivotConfig.getValueColumnIndex(), tableName);
    }

    private String firstConfiguredTableKey(PivotConfigDTO pivotConfig) {
        return pivotConfig.getValueColumnIndex().entrySet().stream()
                .filter(entry -> entry.getValue() != null)
                .map(Map.Entry::getKey)
                .findFirst()
                .orElseThrow(() -> new BadRequestException("Pivot configuration requires a value column index"));
    }

    private PivotMode resolvePivotMode(PivotConfigDTO pivotConfig, String tableName) {
        PivotMode mode = resolveConfiguredValue(pivotConfig.getMode(), tableName);
        return mode == null ? PivotMode.TRANSPOSE : mode;
    }

    private String resolvePrefix(PivotConfigDTO pivotConfig, String tableName) {
        String prefix = resolveConfiguredValue(pivotConfig.getPrefix(), tableName);
        return prefix == null ? "" : prefix;
    }

    private PivotValueFormat resolveValueFormat(PivotConfigDTO pivotConfig, String tableName) {
        PivotValueFormat valueFormat = resolveConfiguredValue(pivotConfig.getValueFormat(), tableName);
        return valueFormat == null ? PivotValueFormat.TRUE_FALSE : valueFormat;
    }

    private <T> T resolveConfiguredValue(Map<String, T> values, String tableName) {
        return ColumnNames.configuredFor(values, tableName);
    }

    private boolean hasPivotConfig(PivotConfigDTO pivotConfig) {
        return pivotConfig != null
                && pivotConfig.getValueColumnIndex() != null
                && !pivotConfig.getValueColumnIndex().isEmpty();
    }

}
