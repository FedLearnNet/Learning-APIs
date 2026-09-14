package bio.cosy.feddb.local.api.importer.files.progress;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ImportTableDTO {
    private String name;
    private int position;
    private int total;
    private ImportTableState state;
    private Long rows;
    private Integer columns;
    private Long rowsPerSecond;
    private Long missingValues;
    private Long durationMs;
    private String note;

    public ImportTableDTO(String name, int position, int total, ImportTableState state) {
        this.name = name;
        this.position = position;
        this.total = total;
        this.state = state;
    }

    public static ImportTableDTO pending(String name, int position, int total) {
        return new ImportTableDTO(name, position, total, ImportTableState.PENDING);
    }

    public static ImportTableDTO reading(String name, int position, int total) {
        return new ImportTableDTO(name, position, total, ImportTableState.READING);
    }

    public static ImportTableDTO reading(
            String name, int position, int total,
            Long rows, Integer columns, Long rowsPerSecond
    ) {
        return new ImportTableDTO(name, position, total, ImportTableState.READING,
                rows, columns, rowsPerSecond, null, null, null);
    }

    public static ImportTableDTO read(
            String name, int position, int total,
            long rows, int columns, Long missingValues, long durationMs
    ) {
        return new ImportTableDTO(name, position, total, ImportTableState.READ,
                rows, columns, null, missingValues, durationMs, null);
    }

    public static ImportTableDTO skipped(String name, int position, int total, String note, long durationMs) {
        return new ImportTableDTO(name, position, total, ImportTableState.SKIPPED,
                null, null, null, null, durationMs, note);
    }

    public static ImportTableDTO failed(String name, int position, int total, String note, long durationMs) {
        return new ImportTableDTO(name, position, total, ImportTableState.FAILED,
                null, null, null, null, durationMs, note);
    }
}
