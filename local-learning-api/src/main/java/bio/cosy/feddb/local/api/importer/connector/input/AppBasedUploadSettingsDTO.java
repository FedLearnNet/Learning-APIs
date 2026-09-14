package bio.cosy.feddb.local.api.importer.connector.input;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class AppBasedUploadSettingsDTO extends ConnectorInputConfigDTO {

    private LinkedHashMap<String, Object> hyperParams;
    private Map<String, Object> inputData;
    private String appImage;
    private Integer appVersionId;
    private String appTitle;

    private LinkedHashMap<String, Object> outputParams;

    public AppBasedUploadSettingsDTO(String mode) {
        super(mode);
    }
}
