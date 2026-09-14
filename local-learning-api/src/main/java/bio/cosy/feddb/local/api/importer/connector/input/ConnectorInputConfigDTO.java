package bio.cosy.feddb.local.api.importer.connector.input;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.EXISTING_PROPERTY,
        property = "mode",
        visible = true
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = FileUploadSettingsDTO.class, name = "FILE"),
        @JsonSubTypes.Type(value = FTPUploadSettingsDTO.class, name = "FTP"),
        @JsonSubTypes.Type(value = FunctionUploadSettingsDTO.class, name = "FUNCTION"),
        @JsonSubTypes.Type(value = AppBasedUploadSettingsDTO.class, name = "APP")
})
public abstract class ConnectorInputConfigDTO {
    private String mode;
}
