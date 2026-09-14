package bio.cosy.feddb.local.api.importer.run.preview;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ConnectorPreviewWalk {
    private ConnectorPreviewTransformationStageData stage;
    private String fingerprint;
}
