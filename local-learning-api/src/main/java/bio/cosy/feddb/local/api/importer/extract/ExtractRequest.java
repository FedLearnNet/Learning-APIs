package bio.cosy.feddb.local.api.importer.extract;

import bio.cosy.feddb.local.api.importer.connector.input.ConnectorInputConfigDTO;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
public class ExtractRequest {

    private ConnectorInputConfigDTO config;
    private Long cohortId;
    private SheetMergeResultDTO mergeConfig;
    private PivotConfigDTO pivotConfig;
    private Map<String, UploadInfoDTO> uploadInfo;
    private Integer limit;
    private boolean preview;

    /**
     * Connector run this extraction belongs to, when it is part of an import run.
     *
     * <p>An app-based extractor is a container execution like an app-based transformer, so its
     * step belongs under the run that started it - otherwise the extraction is invisible in the
     * run log and its status updates have no run to notify. Preview extractions have no run.</p>
     */
    private Long connectorRunId;

    /** Connector to record the app's stored outputs on, when the extraction has one. */
    private Long connectorId;

    private String keycloakId;

    public ExtractRequest(ConnectorInputConfigDTO config, Long cohortId, SheetMergeResultDTO mergeConfig) {
        this(config, cohortId, mergeConfig, null, null, null, false);
    }

    public ExtractRequest(
            ConnectorInputConfigDTO config,
            Long cohortId,
            SheetMergeResultDTO mergeConfig,
            PivotConfigDTO pivotConfig
    ) {
        this(config, cohortId, mergeConfig, pivotConfig, null, null, false);
    }

    public ExtractRequest(
            ConnectorInputConfigDTO config,
            Long cohortId,
            SheetMergeResultDTO mergeConfig,
            PivotConfigDTO pivotConfig,
            Map<String, UploadInfoDTO> uploadInfo
    ) {
        this(config, cohortId, mergeConfig, pivotConfig, uploadInfo, null, false);
    }

    public ExtractRequest(
            ConnectorInputConfigDTO config,
            Long cohortId,
            SheetMergeResultDTO mergeConfig,
            Integer limit,
            boolean preview
    ) {
        this(config, cohortId, mergeConfig, null, null, limit, preview);
    }

    public ExtractRequest(
            ConnectorInputConfigDTO config,
            Long cohortId,
            SheetMergeResultDTO mergeConfig,
            PivotConfigDTO pivotConfig,
            Map<String, UploadInfoDTO> uploadInfo,
            Integer limit,
            boolean preview
    ) {
        this(config, cohortId, mergeConfig, pivotConfig, uploadInfo, limit, preview, null, null, null);
    }

    public ExtractRequest(
            ConnectorInputConfigDTO config,
            Long cohortId,
            SheetMergeResultDTO mergeConfig,
            PivotConfigDTO pivotConfig,
            Map<String, UploadInfoDTO> uploadInfo,
            Integer limit,
            boolean preview,
            Long connectorRunId,
            Long connectorId,
            String keycloakId
    ) {
        this.config = config;
        this.cohortId = cohortId;
        this.mergeConfig = mergeConfig;
        this.pivotConfig = pivotConfig;
        this.uploadInfo = uploadInfo;
        this.limit = limit;
        this.preview = preview;
        this.connectorRunId = connectorRunId;
        this.connectorId = connectorId;
        this.keycloakId = keycloakId;
    }
}
