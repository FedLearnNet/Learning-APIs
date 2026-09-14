package bio.cosy.feddb.local.api.importer.files.read;

import bio.cosy.feddb.core.api.file.FileParsingSettingsDTO;
import bio.cosy.feddb.core.api.file.FileParsingType;
import bio.cosy.feddb.core.api.file.analytics.ColumnProfile;
import bio.cosy.feddb.local.api.importer.extract.PivotConfigDTO;
import bio.cosy.feddb.local.api.importer.extract.SheetMergeResultDTO;
import bio.cosy.feddb.local.api.importer.extract.UploadInfoDTO;
import bio.cosy.feddb.local.api.importer.files.table.*;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.io.File;
import java.util.*;

import static bio.cosy.feddb.core.api.file.FileHelper.isZipFile;

/**
 * Turns a stored file into tables.
 *
 * <p>This class decides what kind of file it is looking at and applies the steps that are the same
 * whatever it turns out to be - pivoting, merging and profiling. The formats themselves are read by
 * {@link DelimitedTableReaderBO} and {@link ExcelTableReaderBO}.</p>
 */
@ApplicationScoped
public class TabularFileReaderBO extends TableReaderSupport {

    @Inject
    ObjectMapper objectMapper;

    @Inject
    DelimitedTableReaderBO delimitedReader;

    @Inject
    ExcelTableReaderBO excelReader;

    @Inject
    TableDataPivotBO tableDataPivotBO;

    @Inject
    TableDataUploadInfoBO tableDataUploadInfoBO;

    public ParsedTables getByFile(File file, TableReadSpec spec, SheetMergeResultDTO mergeConfig) {
        if (file == null || !file.exists() || spec.settings() == null
                || spec.settings().getFileType() == null) {
            return ParsedTables.empty();
        }

        FileParsingType fileType = spec.settings().getFileType();
        if (fileType != FileParsingType.EXCEL && isZipFile(file)) {
            fileType = FileParsingType.MULTIPLE_CSV_ZIP;
        }

        return switch (fileType) {
            case CSV -> ParsedTables.single(delimitedReader.readFile(file, spec));
            case MULTIPLE_CSV_ZIP -> ParsedTables.of(delimitedReader.readArchive(file, spec, mergeConfig));
            case JSON -> ParsedTables.single(readJson(file, spec));
            case EXCEL -> excelReader.read(file, spec, mergeConfig);
        };
    }

    public TableData getFirstTableData(File file, FileParsingSettingsDTO settings) {
        return getFirstTableData(file, TableReadSpec.whole(settings), null, null, null);
    }

    public TableData getFirstTableData(
            File file,
            TableReadSpec spec,
            SheetMergeResultDTO mergeConfig,
            PivotConfigDTO pivotConfig,
            Map<String, UploadInfoDTO> uploadInfo
    ) {
        boolean pivoting = pivotConfig != null
                && pivotConfig.getValueColumnIndex() != null
                && !pivotConfig.getValueColumnIndex().isEmpty();
        boolean configuring = uploadInfo != null && !uploadInfo.isEmpty();
        if (!pivoting && !configuring) {
            return getByFile(file, spec, mergeConfig).takeFirst();
        }

        ParsedTables tables = getByFile(file, spec.unbounded(), null);
        if (configuring) {
            tables = tableDataUploadInfoBO.apply(tables, uploadInfo);
        }
        if (pivoting) {
            tables = tableDataPivotBO.pivotTables(pivotConfig, tables, spec.preview() ? spec.maxRows() : null);
        }
        return asSingleTable(tables, mergeConfig, spec, sourceName(file));
    }


    public TableData getFirstPreviewTableData(
            Map<String, TableSample> previewData,
            FileParsingSettingsDTO settings,
            Integer maxRows,
            SheetMergeResultDTO mergeConfig,
            PivotConfigDTO pivotConfig
    ) {
        return getFirstPreviewTableData(
                previewData, settings, maxRows, mergeConfig, pivotConfig, null);
    }

    public TableData getFirstPreviewTableData(
            Map<String, TableSample> previewData,
            FileParsingSettingsDTO settings,
            Integer maxRows,
            SheetMergeResultDTO mergeConfig,
            PivotConfigDTO pivotConfig,
            Map<String, UploadInfoDTO> uploadInfo
    ) {
        if (previewData == null || previewData.isEmpty()) {
            return null;
        }
        Map<String, TableData> tables = new LinkedHashMap<>();
        previewData.forEach((sheet, sample) -> tables.put(sheet, sample.toTableData()));
        ParsedTables configured = ParsedTables.of(tables);
        if (uploadInfo != null && !uploadInfo.isEmpty()) {
            configured = tableDataUploadInfoBO.apply(configured, uploadInfo);
        }
        ParsedTables pivoted = tableDataPivotBO.pivotTables(pivotConfig, configured, maxRows);
        return asSingleTable(pivoted, mergeConfig, TableReadSpec.of(settings, maxRows, true),
                "preview sample (1/1)");
    }

    public List<ColumnProfile> getColumnProfiles(TableData tableData) {
        return profiles.profiles(tableData);
    }

    public String getJson(TableData tableData) {
        try {
            return objectMapper.writeValueAsString(tableData == null ? List.of() : tableData.getRows());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialize table data to JSON", e);
        }
    }

    private TableData asSingleTable(
            ParsedTables tables,
            SheetMergeResultDTO mergeConfig,
            TableReadSpec spec,
            String source
    ) {
        if (SheetMergePlan.requested(mergeConfig)) {
            Map<String, TableData> merged = tables.tables();
            TableData result = mergeTables(merged, mergeConfig,
                    spec.preview() ? spec : spec.unprofiled(), source);
            merged.clear();
            return result;
        }

        TableData result = tables.takeFirst();
        if (spec.preview() && result != null && spec.maxRows() != null) {
            TableData limited = result.limit(spec.maxRows());
            if (limited != result) {
                result.close();
                result = limited;
            }
        }
        return spec.preview() && result != null ? profiles.enrich(result, source) : result;
    }

    private TableData readJson(File file, TableReadSpec spec) {
        try (JsonParser parser = objectMapper.getFactory().createParser(file)) {
            if (parser.nextToken() != JsonToken.START_ARRAY) {
                return TableData.empty();
            }

            LinkedHashSet<String> columns = new LinkedHashSet<>();
            TableData table = createTable(new ArrayList<>(), spec);
            long count = 0L;
            while (parser.nextToken() != JsonToken.END_ARRAY && !spec.isFull(count)) {
                Map<String, Object> row = TableData.readRow(objectMapper, parser);
                if (row != null) {
                    columns.addAll(row.keySet());
                    table.appendRow(row);
                    spillIfNeeded(table, spec);
                    count++;
                }
            }
            table.setColumns(new ArrayList<>(columns));

            return spec.profile() ? profiles.enrich(table, sourceName(file)) : table;
        } catch (Exception e) {
            Log.warnf(e, "Could not read JSON file: %s", file.getAbsolutePath());
            return null;
        }
    }
}
