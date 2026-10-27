package bio.cosy.feddb.local.api.importer.run.message;

import bio.cosy.feddb.core.base.BaseDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class ConnectorRunMessagesDTO extends BaseDTO {
    private String message;
    private ConnectorRunMessageLevels severity;
    private Long runId;
    private Long transformerId;
}
