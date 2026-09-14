package bio.cosy.feddb.local.api.importer.run.preview.cache;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Date;
import java.util.List;
import java.util.Map;

@Getter
@AllArgsConstructor
public class ConnectorPreviewTransformationCacheStage {
    private final List<Map<String, Object>> rows;
    private final List<String> columns;
    private final Date cachedAt;
}
