package bio.cosy.feddb.local.api.importer.connector;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TriggerSettingsDTO {
    private ConnectorTriggerTypeEnum type;
    private Long sourceConnectorId;
}
