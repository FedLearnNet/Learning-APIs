package bio.cosy.feddb.local.api.importer.files.reupload;

import bio.cosy.feddb.local.api.importer.files.table.ColumnNames;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.List;

public record ReuploadTableDiffDTO(
        String name,
        ReuploadTableState state,
        List<String> missingColumns,
        List<String> notFoundColumns
) {

    @Schema(description = "How a table fared in the comparison")
    public enum ReuploadTableState {
        MATCHED,
        CHANGED,
        MISSING,
        ADDED
    }

    public static ReuploadTableDiffDTO of(
            String name,
            List<String> missingColumns,
            List<String> notFoundColumns
    ) {
        ReuploadTableState state = missingColumns.isEmpty() && notFoundColumns.isEmpty()
                ? ReuploadTableState.MATCHED
                : ReuploadTableState.CHANGED;
        return new ReuploadTableDiffDTO(name, state, missingColumns, notFoundColumns);
    }

    public static ReuploadTableDiffDTO missing(String name, List<String> columns) {
        return new ReuploadTableDiffDTO(name, ReuploadTableState.MISSING, ColumnNames.unqualify(columns), List.of());
    }

    public static ReuploadTableDiffDTO added(String name, List<String> columns) {
        return new ReuploadTableDiffDTO(name, ReuploadTableState.ADDED, List.of(), ColumnNames.unqualify(columns));
    }

    public boolean matches() {
        return state == ReuploadTableState.MATCHED;
    }
}
