package bio.cosy.feddb.local.api.importer.connector;

import bio.cosy.feddb.local.api.importer.connector.input.ConnectorInputConfigDTO;
import bio.cosy.feddb.local.api.importer.extract.PivotConfigDTO;
import bio.cosy.feddb.local.api.importer.extract.SheetMergeResultDTO;
import bio.cosy.feddb.local.api.importer.mapping.ConnectorMappingDTO;
import bio.cosy.feddb.local.api.importer.transformer.ConnectorTransformerDTO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConnectorConfigDTO {
    private Long connectorId;

    private Long cohortId;
    private ConnectorInputConfigDTO inputConfig;
    private Object fileInfo;
    private List<ConnectorTransformerDTO> transformer;
    private List<ConnectorMappingDTO> schemaMapping;
    private SheetMergeResultDTO mergeConfig;
    private PivotConfigDTO pivotConfig;
}
