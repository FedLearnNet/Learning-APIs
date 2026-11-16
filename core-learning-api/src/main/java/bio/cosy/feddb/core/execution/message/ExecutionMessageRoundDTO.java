package bio.cosy.feddb.core.execution.message;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** A message a participant sent or received in a round of a federated run. */
@EqualsAndHashCode(callSuper = true)
@Data
public class ExecutionMessageRoundDTO extends ExecutionMessageDTO {
    @JsonFormat(with = JsonFormat.Feature.ACCEPT_CASE_INSENSITIVE_VALUES)
    private ExecutionRoundDirection direction;
    private Integer round;
    private String fromParticipant;
    private String toParticipant;
    private String communicationId;
    private String payloadPreview;
}
