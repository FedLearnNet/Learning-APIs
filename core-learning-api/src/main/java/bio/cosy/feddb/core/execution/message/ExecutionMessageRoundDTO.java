package bio.cosy.feddb.core.execution.message;

import lombok.Data;
import lombok.EqualsAndHashCode;

/** A message a participant sent or received in a round of a federated run. */
@EqualsAndHashCode(callSuper = true)
@Data
public class ExecutionMessageRoundDTO extends ExecutionMessageDTO {
    // "SEND" or "RECEIVE", seen from the participant that reported it.
    private String direction;
    private Integer round;
    private String fromParticipant;
    private String toParticipant;
    private String communicationId;
    private String payloadPreview;
}
