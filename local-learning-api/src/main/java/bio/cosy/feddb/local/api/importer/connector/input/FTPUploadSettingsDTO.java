package bio.cosy.feddb.local.api.importer.connector.input;

import bio.cosy.feddb.core.api.file.FileParsingSettingsDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class FTPUploadSettingsDTO extends ConnectorInputConfigDTO {

    private String host;
    private Integer port;
    private String username;
    private String password;
    private String filePath;
    private FileParsingSettingsDTO fileSettings;
}
