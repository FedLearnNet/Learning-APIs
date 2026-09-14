package bio.cosy.feddb.local.api.importer.files.progress;

import bio.cosy.feddb.local.api.importer.files.upload.ConnectorFileImportResultDTO;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

/**
 * Something an import just did.
 *
 * <p>Events are what the import publishes as it runs; {@link ImportProgressDTO} is the same story
 * folded up for someone arriving late. Every event carries the phase, so a client that joins mid-way
 * needs no other state.</p>
 */
@Data
@NoArgsConstructor
public class ImportEventDTO {

    private String importId;

    private ImportPhase phase;

    private Instant at = Instant.now();

    @Schema(description = "The tables the file holds, sent once they are all known")
    private List<ImportTableDTO> tables;

    @Schema(description = "One table that has changed state")
    private ImportTableDTO table;

    @Schema(description = "What the import produced, on the last event of a finished import")
    private ConnectorFileImportResultDTO result;

    private String errorMessage;

    @Schema(description = "Whether this is the last event; the stream closes after it")
    private boolean last;

    public static ImportEventDTO of(String importId, ImportPhase phase) {
        ImportEventDTO event = new ImportEventDTO();
        event.importId = importId;
        event.phase = phase;
        return event;
    }
}
