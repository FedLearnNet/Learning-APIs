package bio.cosy.feddb.local.api.importer.files.upload;

import bio.cosy.feddb.local.api.importer.files.ConnectorFilesDetailDTO;
import bio.cosy.feddb.local.api.importer.files.reupload.ConnectorFilesReuploadErrorDTO;
import lombok.Data;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.List;


@Data
public class ConnectorFileImportResultDTO {

    private List<ConnectorFilesDetailDTO> files = List.of();

    @Schema(description = "Unset when no connector was named; false when a replacement was refused")
    private Boolean accepted;

    @Schema(description = "Why a replacement was refused; unset otherwise")
    private ConnectorFilesReuploadErrorDTO error;

    public static ConnectorFileImportResultDTO stored(List<ConnectorFilesDetailDTO> files) {
        ConnectorFileImportResultDTO result = new ConnectorFileImportResultDTO();
        result.setFiles(files);
        return result;
    }

    public static ConnectorFileImportResultDTO accepted(ConnectorFilesDetailDTO file) {
        ConnectorFileImportResultDTO result = new ConnectorFileImportResultDTO();
        result.setFiles(List.of(file));
        result.setAccepted(true);
        return result;
    }

    public static ConnectorFileImportResultDTO refused(ConnectorFilesReuploadErrorDTO error) {
        ConnectorFileImportResultDTO result = new ConnectorFileImportResultDTO();
        result.setAccepted(false);
        result.setError(error);
        return result;
    }
}
