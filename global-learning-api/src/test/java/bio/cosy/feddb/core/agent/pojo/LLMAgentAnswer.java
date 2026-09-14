package bio.cosy.feddb.core.agent.pojo;

import bio.cosy.feddb.core.api.model.chat.ToolDTO;
import bio.cosy.feddb.core.api.model.workflow.chat.HumanInTheLoopDTO;
import bio.cosy.feddb.core.api.model.workflow.chat.ModelWorkflowChatMessageDTO;
import bio.cosy.feddb.core.api.model.workflow.chat.ReasoningDTO;
import bio.cosy.feddb.core.api.model.workflow.chat.UiActionDTO;
import de.unihamburg.daibetes.agent.anlysis.ModelWorkflowAgent;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
public class LLMAgentAnswer {
    private String message;

    private boolean isRequest;
    private boolean isDone;
    private boolean waitingForHitl;

    private String errorMessage;
    private String statusMessage;

    private UiActionDTO action;
    private List<ToolDTO> tools;
    private List<ReasoningDTO> reasonings;
    private List<HumanInTheLoopDTO> humanInTheLoop;

    public LLMAgentAnswer(ModelWorkflowChatMessageDTO dto) {
        if (dto == null) {
            this.tools = new ArrayList<>();
            this.humanInTheLoop = new ArrayList<>();
            return;
        }

        this.message = dto.getMessage();
        this.isRequest = dto.isRequest();
        this.isDone = dto.isDone();
        this.errorMessage = dto.getErrorMessage();
        this.statusMessage = dto.getStatusMessage();
        this.waitingForHitl = isHitlStatus(dto.getStatusMessage());
        this.reasonings = dto.getReasonings();
        this.action = dto.getAction();
        this.tools = dto.getTools() == null ? new ArrayList<>() : new ArrayList<>(dto.getTools());
        this.humanInTheLoop = dto.getHumanInTheLoop() == null ? new ArrayList<>() : new ArrayList<>(dto.getHumanInTheLoop());
    }

    private static boolean isHitlStatus(String statusMessage) {
        return statusMessage != null
                && statusMessage.equalsIgnoreCase(ModelWorkflowAgent.HITL_STATUS_TEXT);
    }
}
