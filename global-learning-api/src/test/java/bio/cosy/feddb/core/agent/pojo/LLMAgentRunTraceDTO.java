package bio.cosy.feddb.core.agent.pojo;

import bio.cosy.feddb.core.api.model.workflow.chat.ModelWorkflowChatMessageDTO;
import bio.cosy.feddb.core.api.model.workflow.chat.UiActionDTO;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;


@Data
@NoArgsConstructor
public class LLMAgentRunTraceDTO {

    private String askedQuestion;
    private String providedHitlAnswer;

    private String finalAnswer;
    private String statusMessage;
    private String errorMessage;

    private boolean waitingForHitl;
    private boolean done;

    private UiActionDTO uiAction;

    private List<ModelWorkflowChatMessageDTO> messages = new ArrayList<>();
}
