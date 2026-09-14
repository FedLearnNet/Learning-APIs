package bio.cosy.feddb.local.api.importer.run.preview;

import bio.cosy.feddb.core.api.file.analytics.ColumnProfile;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;
import java.util.Map;

@Getter
@AllArgsConstructor
public class ConnectorPreviewTransformationStageData {
    private final List<Map<String, Object>> rows;
    private final List<String> columns;
    private final List<ColumnProfile> profiles;
}
