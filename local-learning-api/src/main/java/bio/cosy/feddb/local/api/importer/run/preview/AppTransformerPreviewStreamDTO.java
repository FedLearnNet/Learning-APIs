package bio.cosy.feddb.local.api.importer.run.preview;

import bio.cosy.feddb.local.execution.run.ExecutionRunDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class AppTransformerPreviewStreamDTO extends ExecutionRunDTO {
    private Integer stepIndex;
    private Integer rowCount;
    private boolean finished;
}
