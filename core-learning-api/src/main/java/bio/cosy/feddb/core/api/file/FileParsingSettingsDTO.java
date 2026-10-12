package bio.cosy.feddb.core.api.file;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Locale;


@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class FileParsingSettingsDTO {

    @NotNull
    private FileParsingType fileType;

    @Size(max = 4)
    private String delimiter = ",";

    @Size(max = 4)
    private String customDelimiter;

    private boolean hasHeader = true;

    @NotNull
    private Boolean firstSheetOnly = true;

    public static FileParsingSettingsDTO forFileName(String fileName) {
        String name = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        FileParsingSettingsDTO settings = new FileParsingSettingsDTO();
        if (name.endsWith(".xlsx") || name.endsWith(".xls")) {
            settings.setFileType(FileParsingType.EXCEL);
        } else if (name.endsWith(".json")) {
            settings.setFileType(FileParsingType.JSON);
        } else if (name.endsWith(".zip")) {
            settings.setFileType(FileParsingType.MULTIPLE_CSV_ZIP);
            settings.setFirstSheetOnly(false);
        } else {
            settings.setFileType(FileParsingType.CSV);
        }
        return settings;
    }
}
