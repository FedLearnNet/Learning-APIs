package bio.cosy.feddb.local.api.importer.connector;

import bio.cosy.feddb.local.api.importer.connector.input.ConnectorInputConfigDTO;
import bio.cosy.feddb.local.api.importer.extract.PivotConfigDTO;
import bio.cosy.feddb.local.api.importer.extract.SheetMergeResultDTO;
import bio.cosy.feddb.local.api.importer.files.ConnectorFileUploadSettingsDTO;
import bio.cosy.feddb.local.api.importer.mapping.ConnectorMappingDTO;
import bio.cosy.feddb.local.api.importer.transformer.ConnectorTransformerDTO;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
public class ConnectorConfigDTO {
    private Long connectorId;

    private Long cohortId;
    private ConnectorInputConfigDTO inputConfig;
    private Object fileInfo;
    private List<ConnectorTransformerDTO> transformer;
    private List<ConnectorMappingDTO> schemaMapping;
    private SheetMergeResultDTO mergeConfig;
    private PivotConfigDTO pivotConfig;

    /** Rows the wizard preview shows (display only, never stored); {@code null} means 10. */
    private Integer previewRows;

    public ConnectorConfigDTO(Long connectorId,
                              Long cohortId,
                              ConnectorInputConfigDTO inputConfig,
                              Object fileInfo,
                              List<ConnectorTransformerDTO> transformer,
                              List<ConnectorMappingDTO> schemaMapping,
                              SheetMergeResultDTO mergeConfig,
                              PivotConfigDTO pivotConfig) {
        this.connectorId = connectorId;
        this.cohortId = cohortId;
        this.inputConfig = inputConfig;
        this.fileInfo = fileInfo;
        this.transformer = transformer;
        this.schemaMapping = schemaMapping;
        this.mergeConfig = mergeConfig;
        this.pivotConfig = pivotConfig;
    }

    public int previewRowsOrDefault() {
        if (previewRows == null) {
            return ConnectorFileUploadSettingsDTO.DEFAULT_PREVIEW_ROWS;
        }
        return Math.max(1, Math.min(ConnectorFileUploadSettingsDTO.MAX_PREVIEW_ROWS, previewRows));
    }

}
