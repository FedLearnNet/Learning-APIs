package bio.cosy.feddb.local.api.importer.transformer.session;

import bio.cosy.feddb.local.api.importer.transformer.ConnectorTransformerDTO;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AppTransformerSession {
    private Long stepId;
    private ConnectorTransformerDTO transformer;
}
