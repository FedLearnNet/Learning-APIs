package bio.cosy.feddb.core.api.model.workflow.chat;

import bio.cosy.feddb.core.base.BaseDTO;
import lombok.Data;

@Data
public class DataAnalysisChatWrapperDTO<T extends BaseDTO> {
    private ModelWorkflowChatMessageType type;
    private T message;
}
