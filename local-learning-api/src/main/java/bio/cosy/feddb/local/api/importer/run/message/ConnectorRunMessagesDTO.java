package bio.cosy.feddb.local.api.importer.run.message;

import bio.cosy.feddb.core.api.run.message.RunMessageDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class ConnectorRunMessagesDTO extends RunMessageDTO {

    private ConnectorRunMessageLevels severity;
    private Long transformerId;
    private Long stepId;
}
