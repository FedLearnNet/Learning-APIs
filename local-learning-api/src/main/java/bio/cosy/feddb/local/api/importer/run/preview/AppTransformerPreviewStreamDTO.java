package bio.cosy.feddb.local.api.importer.run.preview;

import bio.cosy.feddb.local.api.importer.run.step.ConnectorRunStepDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class AppTransformerPreviewStreamDTO extends ConnectorRunStepDTO {
    private Integer stepIndex;
    private Integer rowCount;
    private boolean finished;
}
