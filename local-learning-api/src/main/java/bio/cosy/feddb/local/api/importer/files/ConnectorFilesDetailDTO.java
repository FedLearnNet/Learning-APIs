package bio.cosy.feddb.local.api.importer.files;

import bio.cosy.feddb.local.api.importer.run.ConnectorRunDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
public class ConnectorFilesDetailDTO extends ConnectorFilesDTO {

    @Schema(description = "False when the file does not exist or could not be loaded")
    private Boolean fileExists;

    private List<ConnectorFileUploadInfoDTO> uploadInfo;

    private List<ConnectorRunDTO> runs;
}
