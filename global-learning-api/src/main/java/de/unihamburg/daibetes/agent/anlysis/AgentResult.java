package de.unihamburg.daibetes.agent.anlysis;

import bio.cosy.feddb.core.api.model.workflow.chat.UiActionDTO;
import bio.cosy.feddb.core.api.model.workflow.chat.UiActionType;
import de.unihamburg.daibetes.agent.anlysis.tools.PlanNextStepDecision;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AgentResult {
    private String messageMarkdown;
    private UiActionDTO uiAction;
    private boolean needsHumanInTheLoopDTO = false;

    public AgentResult(String messageMarkdown) {
        this.messageMarkdown = messageMarkdown;
    }

    public AgentResult(boolean needsHumanInTheLoopDTO) {
        this.needsHumanInTheLoopDTO = needsHumanInTheLoopDTO;
    }

    public static AgentResult humanInTheLoopNeeded() {
        return new AgentResult(true);
    }

    public static AgentResult fromMessage(String message) {
        return new AgentResult(message);
    }

    public static AgentResult fromMessageAndNextStep(String message, PlanNextStepDecision nextStep) {
        UiActionDTO uiAction = new UiActionDTO();
        uiAction.setAction(UiActionType.ADD_TO_WORKFLOW);
        uiAction.setParams(nextStep.getHyperParams());
        uiAction.setKind(nextStep.getKind());
        uiAction.setRelatedId(nextStep.getStoreId());
        AgentResult agentResult = new AgentResult(message);
        agentResult.setUiAction(uiAction);
        return agentResult;
    }
}
