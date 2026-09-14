package bio.cosy.feddb.local.api.importer.files;

import bio.cosy.feddb.core.api.file.FileParsingSettingsDTO;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ConnectorFileUploadSettingsDTO extends FileParsingSettingsDTO {
    public static final int DEFAULT_PREVIEW_ROWS = 10;
    public static final int MAX_PREVIEW_ROWS = 100;

    private Boolean hasSupportFile = false;

    private Boolean deleteUnneededFileAfterSuccess = true;

    private Boolean supportFile = false;

    @Min(1)
    @Max(MAX_PREVIEW_ROWS)
    private Integer previewRows = DEFAULT_PREVIEW_ROWS;

    public static ConnectorFileUploadSettingsDTO forFileName(String fileName) {
        FileParsingSettingsDTO parsing = FileParsingSettingsDTO.forFileName(fileName);
        ConnectorFileUploadSettingsDTO settings = new ConnectorFileUploadSettingsDTO();
        settings.setFileType(parsing.getFileType());
        settings.setDelimiter(parsing.getDelimiter());
        settings.setCustomDelimiter(parsing.getCustomDelimiter());
        settings.setHasHeader(parsing.isHasHeader());
        settings.setFirstSheetOnly(parsing.getFirstSheetOnly());
        return settings;
    }

    public int previewRowsOrDefault() {
        return previewRows == null ? DEFAULT_PREVIEW_ROWS : previewRows;
    }

    public FileParsingSettingsDTO toFileParsingSettings() {
        FileParsingSettingsDTO settings = new FileParsingSettingsDTO();
        settings.setFileType(getFileType());
        settings.setDelimiter(getDelimiter());
        settings.setCustomDelimiter(getCustomDelimiter());
        settings.setHasHeader(isHasHeader());
        settings.setFirstSheetOnly(getFirstSheetOnly());
        return settings;
    }
}
