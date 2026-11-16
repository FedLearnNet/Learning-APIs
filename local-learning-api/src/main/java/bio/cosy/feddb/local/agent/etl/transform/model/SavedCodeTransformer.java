package bio.cosy.feddb.local.agent.etl.transform.model;

import bio.cosy.feddb.local.api.importer.transformer.ConnectorTransformerDTO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SavedCodeTransformer {
    private boolean saved;
    private String message;
    private ConnectorTransformerDTO transformer;
    private String mappedColumn;

    public static SavedCodeTransformer failed(String message) {
        return new SavedCodeTransformer(false, message, null, null);
    }
}
