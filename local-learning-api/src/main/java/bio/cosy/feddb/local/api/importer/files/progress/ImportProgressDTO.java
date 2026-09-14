package bio.cosy.feddb.local.api.importer.files.progress;

import bio.cosy.feddb.local.api.importer.files.reupload.ConnectorFilesReuploadErrorDTO;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
public class ImportProgressDTO {

    @Schema(description = "The id the client gave the import when it started it")
    private String importId;

    private Long cohortId;

    @Schema(description = "Set when the file is imported as a connector's input")
    private Long connectorId;

    private String fileName;

    private Long fileSize;

    private ImportPhase phase = ImportPhase.PARSING;

    private Instant startedAt = Instant.now();

    private Instant updatedAt = Instant.now();

    private Instant finishedAt;

    @Schema(description = "The file's tables, in file order, each as far as it has got")
    private List<ImportTableDTO> tables = new ArrayList<>();

    @Schema(description = "The stored file, once there is one")
    private Long fileId;

    @Schema(description = "Whether a connector took the file. Absent for an import that named none")
    private Boolean accepted;

    @Schema(description = "Why a connector refused the file")
    private ConnectorFilesReuploadErrorDTO refusal;

    private String errorMessage;

    public ImportProgressDTO(
            String importId,
            Long cohortId,
            Long connectorId,
            String fileName,
            Long fileSize
    ) {
        this.importId = importId;
        this.cohortId = cohortId;
        this.connectorId = connectorId;
        this.fileName = fileName;
        this.fileSize = fileSize;
    }

    /**
     * Derived from the phase; on the wire it would be a second answer to the same question.
     */
    @JsonIgnore
    public boolean isFinished() {
        return phase != null && phase.isFinished();
    }
}
