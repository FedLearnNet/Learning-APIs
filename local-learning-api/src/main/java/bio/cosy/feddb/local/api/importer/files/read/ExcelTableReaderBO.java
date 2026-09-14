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
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Reads workbooks, one sheet per logical table. */
@ApplicationScoped
public class ExcelTableReaderBO extends TableReaderSupport {

    public ExcelTableReaderBO() {
    }

    public ExcelTableReaderBO(FLNetClientConfig config, TableDataProfileBO profiles, TableDataMergeBO merges) {
        super(config, profiles, merges);
    }

    public ParsedTables read(File file, TableReadSpec spec, SheetMergeResultDTO mergeConfig) {
        try (InputStream in = Files.newInputStream(file.toPath());
             Workbook workbook = WorkbookFactory.create(in)) {

            boolean merging = SheetMergePlan.requested(mergeConfig);
            int sheetCount = workbook.getNumberOfSheets();

            if (!merging && !spec.allTables()) {
                if (sheetCount == 0) {
                    return ParsedTables.single(TableData.empty());
                }
                Sheet sheet = workbook.getSheetAt(0);
                reportTables(spec, List.of(sheet.getSheetName()));
                return ParsedTables.single(readReportedSheet(sheet, spec, file, 1, 1));
            }

            // A merge profiles its own result, so profiling each source sheet on the way in would be
            // work nobody reads.
            TableReadSpec sheetSpec = merging ? spec.unprofiled() : spec;
            Map<String, TableData> sheets = new LinkedHashMap<>();
            reportTables(spec, sheetNames(workbook, sheetCount));
            for (int index = 0; index < sheetCount; index++) {
                Sheet sheet = workbook.getSheetAt(index);
                sheets.put(sheet.getSheetName(),
                        readReportedSheet(sheet, sheetSpec, file, index + 1, sheetCount));
            }
            return merging
                    ? ParsedTables.single(mergeTables(sheets, mergeConfig, spec,
                            sourceName(file, mergedTableName())))
                    : ParsedTables.of(sheets);
        } catch (Exception e) {
            Log.warnf(e, "Could not read Excel file: %s", file.getAbsolutePath());
            return ParsedTables.empty();
        }
    }

    /** One sheet, told to whoever is watching the import: workbooks are read a sheet at a time. */
    private TableData readReportedSheet(
            Sheet sheet,
            TableReadSpec spec,
            File file,
            int position,
            int total
    ) {
        String name = sheet.getSheetName();
        ImportPhaseTimer timer = ImportPhaseTimer.silent("Read");
        reportTable(spec, ImportTableDTO.reading(name, position, total));
        TableData table = readSheet(sheet, spec, sourceName(file, name, position, total),
                name, position, total);
        if (table == null || table.getColumns().isEmpty() || table.longSize() == 0) {
            reportTable(spec, ImportTableDTO.skipped(name, position, total,
                    "no usable table", timer.elapsedMillis()));
            return table;
        }
        reportTable(spec, ImportTableDTO.read(name, position, total, table.longSize(),
                table.getColumns().size(), missingValues(table), timer.elapsedMillis()));
        return table;
    }

    private static List<String> sheetNames(Workbook workbook, int sheetCount) {
        List<String> names = new ArrayList<>(sheetCount);
        for (int index = 0; index < sheetCount; index++) {
            names.add(workbook.getSheetAt(index).getSheetName());
        }
        return names;
    }

    private TableData readSheet(
            Sheet sheet,
            TableReadSpec spec,
            String source,
            String table,
            int position,
            int total
    ) {
        DataFormatter formatter = new DataFormatter();
        int firstRowNum = sheet == null ? -1 : findFirstNonEmptyRow(sheet, formatter);
        if (firstRowNum < 0 || sheet.getLastRowNum() < firstRowNum) {
            return TableData.empty();
        }

        Row firstRow = sheet.getRow(firstRowNum);
        int width = firstRow == null ? 0 : Math.max(0, firstRow.getLastCellNum());
        List<String> columns = new ArrayList<>(width);
        for (int column = 0; column < width; column++) {
            columns.add(spec.hasHeader()
                    ? ColumnNames.column(formatter.formatCellValue(cell(firstRow, column)))
                    : String.valueOf(column));
        }
        int startRow = spec.hasHeader() && firstRow != null ? firstRowNum + 1 : firstRowNum;

        TableData tableData = createTable(columns, spec);
        reportTable(spec, ImportTableDTO.reading(table, position, total, 0L, columns.size(), null));
        try (ShardedRowProfiler profiler = spec.profile()
                ? profiles.rowProfiler(columns, source, profileParallelism(1),
                        rowReporter(spec, table, position, total, columns))
                : null) {
            long count = 0L;
            for (int index = startRow; index <= sheet.getLastRowNum(); index++) {
                if (spec.isFull(count)) {
                    break;
                }
                Object[] values = readRow(sheet.getRow(index), columns.size(), formatter);
                // Blank rows are dropped rather than stored, so they must not reach the profile either.
                if (values == null) {
                    continue;
                }
                if (profiler != null) {
                    profiler.accept(values);
                }
                tableData.appendRow(values);
                spillIfNeeded(tableData, spec);
                count++;
            }
            return profiler == null
                    ? tableData
                    : profiles.attach(tableData, profiler.finish(), source);
        }
    }

    /** The row's values in column order, or {@code null} when the row carries no value at all. */
    private static Object[] readRow(Row row, int width, DataFormatter formatter) {
        if (row == null) {
            return null;
        }
        Object[] values = new Object[width];
        boolean anyValue = false;
        for (int column = 0; column < width; column++) {
            values[column] = readCellValue(cell(row, column), formatter);
            anyValue |= values[column] != null && !values[column].toString().isBlank();
        }
        return anyValue ? values : null;
    }

    private static int findFirstNonEmptyRow(Sheet sheet, DataFormatter formatter) {
        for (int index = sheet.getFirstRowNum(); index <= sheet.getLastRowNum(); index++) {
            Row row = sheet.getRow(index);
            if (row != null && readRow(row, Math.max(0, row.getLastCellNum()), formatter) != null) {
                return index;
            }
        }
        return -1;
    }

    private static Cell cell(Row row, int column) {
        return row.getCell(column, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
    }

    /**
     * Empty cells inside a real source row read as {@code ""} rather than {@code null}, to match the
     * CSV reader. That is what lets downstream code tell a genuinely empty value in a row apart from
     * the structural null a merge leaves where a table has no such column.
     */
    private static Object readCellValue(Cell cell, DataFormatter formatter) {
        if (cell == null) {
            return "";
        }
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case BOOLEAN -> cell.getBooleanCellValue();
            case NUMERIC -> DateUtil.isCellDateFormatted(cell)
                    ? cell.getLocalDateTimeCellValue().toString()
                    : formatter.formatCellValue(cell);
            case BLANK -> "";
            default -> formatter.formatCellValue(cell);
        };
    }
}
