package bio.cosy.feddb.local.api.importer.files.reupload;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.List;

@Data
@NoArgsConstructor
public class ConnectorFilesReuploadErrorDTO {

    private String message;

    @Schema(description = "Columns present only in the file being replaced")
    private List<String> missingColumns = List.of();

    @Schema(description = "Columns present only in the replacement")
    private List<String> notFoundColumns = List.of();

    @Schema(description = "The comparison table by table, which is where a difference can be acted on")
    private List<ReuploadTableDiffDTO> tables = List.of();

    public ConnectorFilesReuploadErrorDTO(String message) {
        this.message = message;
        this.tables = List.of();
    }

    public ConnectorFilesReuploadErrorDTO(
            String message,
            List<String> missingColumns,
            List<String> notFoundColumns,
            List<ReuploadTableDiffDTO> tables
    ) {
        this.message = message;
        this.missingColumns = missingColumns;
        this.notFoundColumns = notFoundColumns;
        this.tables = tables;
    }
}
