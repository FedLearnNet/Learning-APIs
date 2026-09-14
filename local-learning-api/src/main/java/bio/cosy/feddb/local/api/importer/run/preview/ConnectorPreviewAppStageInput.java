package bio.cosy.feddb.local.api.importer.run.preview;

import bio.cosy.feddb.local.api.importer.transformer.ConnectorTransformerDTO;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@AllArgsConstructor
public class ConnectorPreviewAppStageInput {
    private ConnectorTransformerDTO transformer;
    private List<Map<String, Object>> rows;
    private List<String> columns;
    private String fingerprint;
}
