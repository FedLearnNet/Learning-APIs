package bio.cosy.feddb.local.api.importer.connector.input;

import bio.cosy.feddb.local.api.importer.transformer.ConnectorTransformerDTO;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.List;
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
    private List<String> selectedOutputs;

    @JsonIgnore
    public ConnectorTransformerDTO getExtractor() {
        ConnectorTransformerDTO extractor = new ConnectorTransformerDTO();
        extractor.setAppImage(appImage);
        extractor.setAppVersionId(appVersionId != null ? Long.valueOf(appVersionId) : null);
        return extractor;
    }
}
