package de.unihamburg.daibetes.api.development.run.federated;

import bio.cosy.feddb.core.execution.message.ExecutionRoundDirection;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

/** Live round-message notification sent by the python wrapper. */
@Data
public class FederatedRoundMessageCreateDTO {
    private Long federatedRunId;
    @JsonFormat(with = JsonFormat.Feature.ACCEPT_CASE_INSENSITIVE_VALUES)
    private ExecutionRoundDirection direction;
    private Integer round;
    private String fromParticipant;
    private String toParticipant;
    private String communicationId;
    private String payloadPreview;
}
