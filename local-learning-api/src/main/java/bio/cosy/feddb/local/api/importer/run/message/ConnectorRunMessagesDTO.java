package bio.cosy.feddb.local.api.importer.run.message;

import bio.cosy.feddb.core.execution.message.ExecutionMessageDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class ConnectorRunMessagesDTO extends ExecutionMessageDTO {

    private ConnectorRunMessageLevels severity;
    private Long transformerId;
    private Long stepId;
}
