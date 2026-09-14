package bio.cosy.feddb.local.api.importer.connector.input;

import bio.cosy.feddb.core.api.file.FileParsingSettingsDTO;
import bio.cosy.feddb.core.api.file.FileParsingType;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class FileUploadSettingsDTO extends ConnectorInputConfigDTO {

    /**
     * @deprecated Parsing settings are file-owned and are retained here only for
     * existing persisted connector configurations.
     */
    @Deprecated
    private FileParsingType fileType; // EXCEL | CSV | JSON

    private String delimiter; // , | | | ; | s | \t | CUSTOM
    private ConnectorInputMergeType mergeType; // HORIZONTALLY | VERTICALLY
    private String extractSheets; // ENTIRE | SPECIFIC
    private byte[] file;
    private Long fileId;
    private Boolean hasSupportFile = false;
    private String customDelimiter;
    private boolean hasHeader = false;
    private String specificSheets;
    private Boolean firstSheetOnly = true;

    private Boolean deleteUnneededFileAfterSuccess = false;

    //only for get connector for the FE
    private boolean fileExists = false;

    /**
     * Migration fallback for files created before parsing settings were stored
     * with the file entity.
     */
    public FileParsingSettingsDTO toFileParsingSettings() {
        FileParsingSettingsDTO settings = new FileParsingSettingsDTO();
        settings.setFileType(fileType);
        settings.setDelimiter(delimiter);
        settings.setCustomDelimiter(customDelimiter);
        settings.setHasHeader(hasHeader);
        settings.setFirstSheetOnly(firstSheetOnly);
        return settings;
    }
}
