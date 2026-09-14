package bio.cosy.feddb.local.api.importer.files.reupload;

import bio.cosy.feddb.local.api.importer.files.ConnectorFileUploadInfoDTO;
import bio.cosy.feddb.local.api.importer.files.table.ColumnNames;
import jakarta.enterprise.context.ApplicationScoped;
import org.apache.commons.lang3.StringUtils;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@ApplicationScoped
public class ReuploadValidationBO {

    public ConnectorFilesReuploadErrorDTO validate(
            List<ConnectorFileUploadInfoDTO> storedInfos,
            List<ConnectorFileUploadInfoDTO> newInfos
    ) {
        if (newInfos == null) {
            return new ConnectorFilesReuploadErrorDTO("Uploaded file contains no readable data");
        }

        List<String> expected = columnsOf(storedInfos);
        if (expected.isEmpty()) {
            return new ConnectorFilesReuploadErrorDTO("Could not determine existing file structure");
        }

        List<String> actual = columnsOf(newInfos);
        if (actual.isEmpty()) {
            return new ConnectorFilesReuploadErrorDTO(
                    "Uploaded file contains no readable columns",
                    fileDifference(expected, actual),
                    List.of(),
                    tableDiffs(expected, actual));
        }

        List<ReuploadTableDiffDTO> tables = tableDiffs(expected, actual);
        if (tables.stream().allMatch(ReuploadTableDiffDTO::matches)) {
            return null;
        }

        return new ConnectorFilesReuploadErrorDTO(
                "Uploaded file columns do not match the columns of the file it replaces",
                fileDifference(expected, actual),
                fileDifference(actual, expected),
                tables);
    }

    /** Validates the already-filtered source columns on which the connector depends. */
    public ConnectorFilesReuploadErrorDTO validateUsedColumns(
            List<String> storedColumns,
            List<String> newColumns
    ) {
        List<String> expected = storedColumns == null ? List.of() : storedColumns;
        List<String> actual = newColumns == null ? List.of() : newColumns;
        if (expected.isEmpty() && actual.isEmpty()) {
            return null;
        }

        List<ReuploadTableDiffDTO> tables = tableDiffs(expected, actual);
        if (tables.stream().allMatch(ReuploadTableDiffDTO::matches)) {
            return null;
        }
        return new ConnectorFilesReuploadErrorDTO(
                "Uploaded file columns used by the connector do not match the file it replaces",
                fileDifference(expected, actual),
                fileDifference(actual, expected),
                tables);
    }

    private List<String> columnsOf(List<ConnectorFileUploadInfoDTO> infos) {
        if (infos == null) {
            return List.of();
        }
        return infos.stream()
                .filter(Objects::nonNull)
                .flatMap(info -> qualifiedColumns(info).stream())
                .toList();
    }

    private List<String> qualifiedColumns(ConnectorFileUploadInfoDTO info) {
        if (info.getColumns() == null) {
            return List.of();
        }
        String table = ColumnNames.table(StringUtils.defaultIfBlank(info.getSheet(), "table"));
        return info.getColumns().stream()
                .filter(column -> column != null && !ColumnNames.normalize(column).isEmpty())
                .map(column -> ColumnNames.unqualify(ColumnNames.column(column)))
                .map(column -> ColumnNames.qualify(table, column))
                .toList();
    }


    private List<ReuploadTableDiffDTO> tableDiffs(List<String> expected, List<String> actual) {
        Map<String, List<String>> oldTables = byTable(expected);
        Map<String, List<String>> newTables = byTable(actual);
        if (oldTables.size() == 1 && newTables.size() == 1) {
            var oldTable = oldTables.entrySet().iterator().next();
            return List.of(diff(oldTable.getKey(), oldTable.getValue(),
                    newTables.values().iterator().next()));
        }

        Stream<ReuploadTableDiffDTO> expectedTableDiffs = oldTables.entrySet().stream()
                .map(table -> newTables.containsKey(table.getKey())
                        ? diff(table.getKey(), table.getValue(), newTables.get(table.getKey()))
                        : ReuploadTableDiffDTO.missing(table.getKey(), table.getValue()));
        Stream<ReuploadTableDiffDTO> addedTableDiffs = newTables.entrySet().stream()
                .filter(table -> !oldTables.containsKey(table.getKey()))
                .map(table -> ReuploadTableDiffDTO.added(table.getKey(), table.getValue()));

        return Stream.concat(expectedTableDiffs, addedTableDiffs).toList();
    }

    private Map<String, List<String>> byTable(List<String> columns) {
        return columns.stream().collect(Collectors.groupingBy(
                column -> StringUtils.substringBefore(column, ColumnNames.QUALIFIER),
                LinkedHashMap::new,
                Collectors.toList()));
    }

    private ReuploadTableDiffDTO diff(String name, List<String> expected, List<String> actual) {
        return ReuploadTableDiffDTO.of(name,
                tableDifference(expected, actual), tableDifference(actual, expected));
    }

    private List<String> tableDifference(List<String> from, List<String> against) {
        Set<String> present = against.stream()
                .map(ColumnNames::normalize)
                .collect(Collectors.toSet());
        return from.stream()
                .filter(column -> !present.contains(ColumnNames.normalize(column)))
                .map(ColumnNames::unqualify)
                .toList();
    }

    private List<String> fileDifference(List<String> from, List<String> against) {
        Set<String> present = against.stream()
                .map(ColumnNames::unqualify)
                .map(ColumnNames::normalize)
                .collect(Collectors.toSet());
        return from.stream()
                .filter(column -> !present.contains(ColumnNames.normalize(ColumnNames.unqualify(column))))
                .map(ColumnNames::unqualify)
                .distinct()
                .toList();
    }
}
