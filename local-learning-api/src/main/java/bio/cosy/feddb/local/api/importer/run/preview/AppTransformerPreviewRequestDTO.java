package bio.cosy.feddb.local.api.importer.run.preview;

import bio.cosy.feddb.local.api.importer.connector.ConnectorConfigDTO;
import lombok.Data;

@Data
public class AppTransformerPreviewRequestDTO {
    private ConnectorConfigDTO config;
    private Integer stepIndex;
}
