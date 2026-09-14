package bio.cosy.feddb.local.api.importer.files.read;

import bio.cosy.feddb.local.api.importer.files.ConnectorFileUploadInfoDTO;
import bio.cosy.feddb.local.api.importer.files.table.TableSample;

import java.util.List;
import java.util.Map;

public record FileAnalysis(
        List<ConnectorFileUploadInfoDTO> uploadInfo,
        Map<String, TableSample> previewData
) {
}
