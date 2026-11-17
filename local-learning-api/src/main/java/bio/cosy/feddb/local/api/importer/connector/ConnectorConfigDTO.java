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

    /**
     * How many rows the wizard preview shows. Display-only: never stored with the connector.
     * {@code null} means {@link ConnectorFileUploadSettingsDTO#DEFAULT_PREVIEW_ROWS}.
     */
    private Integer previewRows;

    /**
     * Whether the preview should pick the rows that fill the most columns instead of the first ones.
     * Display-only; {@code null} means {@code false}.
     */
    private Boolean previewNonEmpty;

    /** The connector configuration itself; the preview settings stay at their defaults. */
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

    /** The requested preview row count, clamped to 1..{@link ConnectorFileUploadSettingsDTO#MAX_PREVIEW_ROWS}. */
    public int previewRowsOrDefault() {
        if (previewRows == null) {
            return ConnectorFileUploadSettingsDTO.DEFAULT_PREVIEW_ROWS;
        }
        return Math.max(1, Math.min(ConnectorFileUploadSettingsDTO.MAX_PREVIEW_ROWS, previewRows));
    }

    public boolean previewNonEmptyOrDefault() {
        return Boolean.TRUE.equals(previewNonEmpty);
    }
}
