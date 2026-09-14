package de.unihamburg.daibetes.api.analysis.chat.hitl;

import bio.cosy.feddb.core.base.BaseDTO;
import de.unihamburg.daibetes.agent.anlysis.PlanState;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class HumanInTheLoopRequestDTO extends BaseDTO {
    private String request;
    private String answer;
    private Boolean isAnswered;
    private PlanState state;
    private Long messageId;
}
