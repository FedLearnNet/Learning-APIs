package bio.cosy.feddb.local.api.importer.files;

import bio.cosy.feddb.core.api.file.FileDTO;
import bio.cosy.feddb.core.api.file.FileParsingSettingsDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class ConnectorFilesDTO extends FileDTO {

    private Boolean isSupportFile;

    private Long cohortId;
    private Long connectorId;

    private FileParsingSettingsDTO uploadSettings;
}
