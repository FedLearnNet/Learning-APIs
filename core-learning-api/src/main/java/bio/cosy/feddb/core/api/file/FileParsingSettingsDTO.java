package bio.cosy.feddb.core.api.file;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Locale;

/**
 * Connector-independent settings required to interpret a physical tabular file.
 *
 * <p>The settings belong to the file because changing any of them changes the
 * meaning of its rows and columns. Connector lifecycle and transformation
 * settings deliberately remain outside this DTO.</p>
 */
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

    /**
     * The settings a file name implies, for an upload that arrives without any of its own.
     *
     * <p>An archive is read as all of its tables rather than only the first: an archive with one
     * table in it is the exception, not the rule.</p>
     */
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

    @AssertTrue(message = "A delimiter is required for CSV files")
    @JsonIgnore
    public boolean isDelimiterValid() {
        if (fileType != FileParsingType.CSV && fileType != FileParsingType.MULTIPLE_CSV_ZIP) {
            return true;
        }
        if ("CUSTOM".equals(delimiter)) {
            return customDelimiter != null && !customDelimiter.isBlank();
        }
        return delimiter != null && !delimiter.isBlank();
    }
}
