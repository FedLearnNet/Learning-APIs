package bio.cosy.feddb.local.api.importer.connector.input;


import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class FunctionUploadSettingsDTO extends ConnectorInputConfigDTO {

    private String function;
    private Map<String, Object> parameters;
}
