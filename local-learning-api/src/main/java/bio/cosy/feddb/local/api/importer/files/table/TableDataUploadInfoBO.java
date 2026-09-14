package bio.cosy.feddb.local.api.importer.files.table;

import bio.cosy.feddb.local.api.importer.extract.UploadInfoDTO;
import bio.cosy.feddb.local.api.importer.files.ConnectorFileUploadInfoDTO;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.BadRequestException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/** Applies the column renames and deletions selected during file upload. */
@ApplicationScoped
public class TableDataUploadInfoBO {

    public ParsedTables apply(ParsedTables parsedTables, Map<String, UploadInfoDTO> uploadInfo) {
        if (parsedTables == null || uploadInfo == null || uploadInfo.isEmpty()) {
            return parsedTables;
        }

        Map<String, TableData> configuredTables = new LinkedHashMap<>();
        Map<String, TableData> tables = parsedTables.tables();
        try {
            for (Map.Entry<String, TableData> entry : tables.entrySet()) {
                String tableName = entry.getKey();
                TableData tableData = entry.getValue();
                if (tableData == null) {
                    continue;
                }
                configuredTables.put(
                        tableName,
                        applyToTable(tableData, uploadInfoForTable(uploadInfo, tableName))
                );
            }
            tables.clear();
            return ParsedTables.of(configuredTables);
        } catch (RuntimeException exception) {
            configuredTables.values().forEach(TableData::close);
            parsedTables.close();
            throw exception;
        }
    }

    private TableData applyToTable(TableData source, UploadInfoDTO info) {
        if (source == null || info == null || info.getColumns() == null || info.getColumns().isEmpty()) {
            return source;
        }

        ColumnProjection projection = projection(source.getColumns(), info);
        if (projection.isIdentity()) {
            return source;
        }

        TableData result = TableData.diskBacked(projection.targetColumns(), null, null);
        try {
            try (Stream<Map<String, Object>> rows = source.streamRows()) {
                rows.forEach(row -> result.appendRow(projectRow(row, projection)));
            }
            source.close();
            return result;
        } catch (RuntimeException exception) {
            result.close();
            source.close();
            throw exception;
        }
    }

    private ColumnProjection projection(List<String> sourceColumns, UploadInfoDTO info) {
        List<String> columns = sourceColumns == null ? List.of() : sourceColumns;
        List<String> configuredColumns = info.getColumns();
        List<String> renamedColumns = info.getRenamedColumns();
        List<Boolean> deletedColumns = info.getDeletedColumns();

        List<String> retainedSourceColumns = new ArrayList<>();
        List<String> targetColumns = new ArrayList<>();
        Set<String> uniqueTargets = new LinkedHashSet<>();
        boolean changed = false;

        for (String sourceColumn : columns) {
            int index = configuredColumnIndex(configuredColumns, sourceColumn);
            boolean deleted = index >= 0
                    && deletedColumns != null
                    && index < deletedColumns.size()
                    && Boolean.TRUE.equals(deletedColumns.get(index));
            if (deleted) {
                changed = true;
                continue;
            }

            String targetColumn = index >= 0
                    && renamedColumns != null
                    && index < renamedColumns.size()
                    && renamedColumns.get(index) != null
                    && !renamedColumns.get(index).isBlank()
                    ? renamedColumns.get(index)
                    : sourceColumn;
            if (!uniqueTargets.add(targetColumn)) {
                throw new BadRequestException("Upload column configuration produces duplicate column '"
                        + targetColumn + "'");
            }
            retainedSourceColumns.add(sourceColumn);
            targetColumns.add(targetColumn);
            changed |= !sourceColumn.equals(targetColumn);
        }

        return new ColumnProjection(retainedSourceColumns, targetColumns, !changed);
    }

    private int configuredColumnIndex(List<String> configuredColumns, String sourceColumn) {
        for (int index = 0; index < configuredColumns.size(); index++) {
            String configuredColumn = configuredColumns.get(index);
            if (configuredColumn != null
                    && (configuredColumn.equals(sourceColumn)
                    || baseColumn(configuredColumn).equals(sourceColumn))) {
                return index;
            }
        }
        return -1;
    }

    private Map<String, Object> projectRow(Map<String, Object> row, ColumnProjection projection) {
        Map<String, Object> projected = new LinkedHashMap<>();
        for (int index = 0; index < projection.sourceColumns().size(); index++) {
            String sourceColumn = projection.sourceColumns().get(index);
            projected.put(
                    projection.targetColumns().get(index),
                    row == null ? null : row.get(sourceColumn)
            );
        }
        return projected;
    }

    private UploadInfoDTO uploadInfoForTable(Map<String, UploadInfoDTO> uploadInfo, String tableName) {
        UploadInfoDTO exactMatch = uploadInfo.get(tableName);
        if (exactMatch != null) {
            return exactMatch;
        }
        // Either side may name the table with the file extension its archive entry had.
        String wanted = tableKey(tableName);
        return uploadInfo.entrySet().stream()
                .filter(entry -> tableKey(entry.getKey()).equals(wanted))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElseGet(() -> uploadInfo.size() == 1 ? firstUploadInfo(uploadInfo) : null);
    }

    private static String tableKey(String tableName) {
        return ColumnNames.table(ColumnNames.tableOfFile(tableName));
    }

    private UploadInfoDTO firstUploadInfo(Map<String, UploadInfoDTO> uploadInfo) {
        UploadInfoDTO defaultInfo = uploadInfo.get(ConnectorFileUploadInfoDTO.DEFAULT_SHEET_NAME);
        return defaultInfo != null ? defaultInfo : uploadInfo.values().iterator().next();
    }

    private String baseColumn(String column) {
        return ColumnNames.unqualify(column);
    }

    private record ColumnProjection(
            List<String> sourceColumns,
            List<String> targetColumns,
            boolean isIdentity
    ) {
    }
}
