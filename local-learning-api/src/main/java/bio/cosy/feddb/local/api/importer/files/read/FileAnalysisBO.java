package bio.cosy.feddb.local.api.importer.files.read;

import bio.cosy.feddb.core.api.file.analytics.ColumnProfile;
import bio.cosy.feddb.local.api.importer.ImportPhaseTimer;
import bio.cosy.feddb.local.api.importer.extract.SheetMergeResultDTO;
import bio.cosy.feddb.local.api.importer.files.ConnectorFileUploadInfoDTO;
import bio.cosy.feddb.local.api.importer.files.ConnectorFileUploadSettingsDTO;
import bio.cosy.feddb.local.api.importer.files.progress.ImportPhase;
import bio.cosy.feddb.local.api.importer.files.progress.ImportProgressTracker;
import bio.cosy.feddb.local.api.importer.files.table.ColumnNames;
import bio.cosy.feddb.local.api.importer.files.table.ParsedTables;
import bio.cosy.feddb.local.api.importer.files.table.TableData;
import bio.cosy.feddb.local.api.importer.files.table.TableSample;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@ApplicationScoped
public class FileAnalysisBO {

    @Inject
    TabularFileReaderBO fileHandlerBO;

    @Inject
    ImportProgressTracker progress;

    public FileAnalysisBO() {
        this.progress = new ImportProgressTracker();
    }

    public FileAnalysisBO(TabularFileReaderBO fileHandlerBO) {
        this();
        this.fileHandlerBO = fileHandlerBO;
    }

    /**
     * Parses the file and describes each of its tables: the columns, a sample of the rows, and the
     * column statistics where the spec asked for them.
     *
     * @param uploadRows rows kept per table as the sample shown with the upload
     */
    public FileAnalysis analyze(
            File file,
            TableReadSpec spec,
            SheetMergeResultDTO mergeConfig,
            Integer uploadRows
    ) {
        return analyze(file, spec, mergeConfig, uploadRows, null);
    }

    /**
     * The same analysis, reported to whoever is watching the named import.
     *
     * @param importId the import this read belongs to, or {@code null} when nobody is watching
     */
    public FileAnalysis analyze(
            File file,
            TableReadSpec spec,
            SheetMergeResultDTO mergeConfig,
            Integer uploadRows,
            String importId
    ) {
        String fileLabel = fileLabel(file);
        progress.phase(importId, ImportPhase.PARSING);
        ImportPhaseTimer parseTimer = ImportPhaseTimer.started("Parse", "%s", fileLabel);
        // The readers report each table as they open it, which is the only place that knows.
        ParsedTables parsedTables = fileHandlerBO.getByFile(file, spec.forImport(importId), mergeConfig);
        parseTimer.done("%s -> %d sheet(s)", fileLabel, parsedTables.tables().size());

        if (parsedTables.isEmpty()) {
            // The file exists — it just yielded no readable table. Saying "not found"
            // sends everyone looking for a missing path instead of at the real cause
            // (wrong file type or delimiter, or an archive whose entries are none of
            // the supported table formats).
            throw new NotFoundException(String.format(
                    "No readable table found in '%s' (%d bytes, parsed as %s). Check the file type, the delimiter, "
                            + "and for archives that the entries are delimited text files.",
                    file == null ? "unknown" : file.getName(),
                    file == null ? 0L : file.length(),
                    spec.settings() == null || spec.settings().getFileType() == null
                            ? "unknown" : spec.settings().getFileType()));
        }

        // Each table reported itself as it was read, statistics and all; what is left is the sample.
        int sheetCount = parsedTables.tables().size();
        progress.phase(importId, ImportPhase.SAMPLING);
        ImportPhaseTimer sampleTimer = ImportPhaseTimer.started("Sample", "%s - %d sheet(s)", fileLabel, sheetCount);
        List<ConnectorFileUploadInfoDTO> uploadInfo = new ArrayList<>();
        Map<String, TableSample> previewData = new LinkedHashMap<>();
        try (parsedTables) {
            Set<String> duplicateColumns = findDuplicateColumnNamesAcrossSheets(parsedTables.tables());
            int sheetIndex = 0;
            for (Map.Entry<String, TableData> entry : parsedTables.tables().entrySet()) {
                String sheetName = entry.getKey();
                ImportPhaseTimer sheetTimer = ImportPhaseTimer.silent("Sample");
                sheetIndex++;
                TableData source = entry.getValue() == null ? TableData.empty() : entry.getValue();
                TableData uploadSample = TableSample.from(source, uploadRows).toTableData();
                TableSample previewSample = TableSample.from(
                        source,
                        ConnectorFileUploadSettingsDTO.DEFAULT_PREVIEW_ROWS
                );
                TableData renamedSample = renameDuplicateColumns(uploadSample, sheetName, duplicateColumns);
                if (renamedSample != uploadSample) {
                    uploadSample.close();
                }
                try {
                    ConnectorFileUploadInfoDTO info = new ConnectorFileUploadInfoDTO(
                            sheetName,
                            renamedSample,
                            fileHandlerBO.getJson(renamedSample)
                    );
                    info.setColumnProfiles(renamedSample.getColumnProfiles());
                    uploadInfo.add(info);
                    previewData.put(sheetName, previewSample);
                    sheetTimer.done("%s / %s (%d/%d) sampled", fileLabel, sheetName, sheetIndex, sheetCount);
                } finally {
                    renamedSample.close();
                    if (source != entry.getValue()) {
                        source.close();
                    }
                }
            }
        }
        sampleTimer.done("%s - %d sheet(s) sampled", fileLabel, uploadInfo.size());

        return new FileAnalysis(
                List.copyOf(uploadInfo),
                Collections.unmodifiableMap(new LinkedHashMap<>(previewData))
        );
    }

    public List<ConnectorFileUploadInfoDTO> uploadInfoFromSamples(Map<String, TableSample> samples) {
        if (samples == null || samples.isEmpty()) {
            return List.of();
        }
        List<ConnectorFileUploadInfoDTO> result = new ArrayList<>(samples.size());
        samples.forEach((sheet, sample) -> {
            if (sample == null) {
                return;
            }
            try (TableData tableData = sample.toTableData()) {
                ConnectorFileUploadInfoDTO info = new ConnectorFileUploadInfoDTO(
                        sheet,
                        tableData,
                        fileHandlerBO.getJson(tableData)
                );
                info.setColumnProfiles(tableData.getColumnProfiles());
                result.add(info);
            }
        });
        return List.copyOf(result);
    }

    /** File name and size for the phase logs, so every line names the file it belongs to. */
    private static String fileLabel(File file) {
        return file == null
                ? "unknown file"
                : String.format("%s (%d bytes)", file.getName(), file.length());
    }

    private static Set<String> findDuplicateColumnNamesAcrossSheets(Map<String, TableData> sheets) {
        Map<String, List<String>> columnsBySheet = new LinkedHashMap<>();
        sheets.forEach((sheet, tableData) -> columnsBySheet.put(
                sheet, tableData == null ? List.of() : tableData.getColumns()));
        return ColumnNames.duplicatesAcross(columnsBySheet);
    }

    /** Qualifies the columns a table shares with another one, so the file's columns stay distinct. */
    private static TableData renameDuplicateColumns(
            TableData tableData,
            String sheetName,
            Set<String> duplicateColumns
    ) {
        if (tableData == null || tableData.getColumns() == null
                || tableData.getColumns().stream().noneMatch(duplicateColumns::contains)) {
            return tableData;
        }
        List<String> columns = tableData.getColumns();
        List<String> renamedColumns = columns.stream()
                .map(column -> rename(column, sheetName, duplicateColumns))
                .toList();
        List<Map<String, Object>> renamedRows;
        try (var rows = tableData.streamRows()) {
            renamedRows = rows.map(row -> renameRow(row, columns, renamedColumns)).toList();
        }
        List<ColumnProfile> profiles = tableData.getColumnProfiles() == null
                ? null
                : tableData.getColumnProfiles().stream()
                .map(profile -> profile.withName(rename(profile.name(), sheetName, duplicateColumns)))
                .toList();
        return new TableData(renamedColumns, renamedRows, profiles);
    }

    private static String rename(String column, String sheetName, Set<String> duplicateColumns) {
        return duplicateColumns.contains(column) ? ColumnNames.qualify(sheetName, column) : column;
    }

    private static Map<String, Object> renameRow(
            Map<String, Object> row,
            List<String> columns,
            List<String> renamedColumns
    ) {
        if (row == null) {
            return null;
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index < columns.size(); index++) {
            result.put(renamedColumns.get(index), row.get(columns.get(index)));
        }
        return result;
    }
}
