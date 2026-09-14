package bio.cosy.feddb.core.api.model.workflow.chat;

import bio.cosy.feddb.core.api.model.chat.BaseChatMessageDTO;
import bio.cosy.feddb.core.api.model.chat.ToolDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

@EqualsAndHashCode(callSuper = true)
@Data
public class ModelWorkflowChatMessageDTO extends BaseChatMessageDTO {

    private Long workflowId;
    private UiActionDTO action;
    private List<ReasoningDTO> reasonings;
    private List<ToolDTO> tools;
    private List<HumanInTheLoopDTO> humanInTheLoop;

    public void addReasoning(String reasoning) {
        if (this.reasonings == null) {
            this.reasonings = new ArrayList<>();
        }
        this.reasonings.add(new ReasoningDTO(reasoning));
    }


    public void addReasoning(ReasoningDTO reasoning) {
        if (this.reasonings == null) {
            this.reasonings = new ArrayList<>();
        }
        this.reasonings.add(reasoning);
    }

    public void addHumanInTheLoopAnswer(String answer) {
        if (this.humanInTheLoop == null) {
            this.humanInTheLoop = new ArrayList<>();
        }
        if (!this.humanInTheLoop.isEmpty()) {
            HumanInTheLoopDTO last = this.humanInTheLoop.getLast();
            last.setAnswer(answer);
        }
    }

    public void addTool(ToolDTO tool) {
        if (this.tools == null) {
            this.tools = new ArrayList<>();
        }
        this.tools.add(tool);
    }

    public void updateTool(ToolDTO tool) {
        if (this.tools == null) {
            addTool(tool);
            return;
        }
        for (int i = 0; i < this.tools.size(); i++) {
            if (this.tools.get(i).getId().equals(tool.getId())) {
                this.tools.set(i, tool);
                return;
            }
        }
        this.tools.add(tool);
    }

    public static ModelWorkflowChatMessageDTO copy(ModelWorkflowChatMessageDTO source) {
        ModelWorkflowChatMessageDTO copy = BaseChatMessageDTO.copy(source);
        copy.setWorkflowId(source.getWorkflowId());
        copy.setAction(source.getAction());
        copy.setTools(source.getTools());
        copy.setReasonings(source.getReasonings());
        copy.setHumanInTheLoop(source.getHumanInTheLoop());
        return copy;
    }

    public static ModelWorkflowChatMessageDTO snapshot(
            ModelWorkflowChatMessageDTO state,
            Consumer<ModelWorkflowChatMessageDTO> mut
    ) {
        if (mut != null) mut.accept(state);
        return ModelWorkflowChatMessageDTO.copy(state);
    }
}
