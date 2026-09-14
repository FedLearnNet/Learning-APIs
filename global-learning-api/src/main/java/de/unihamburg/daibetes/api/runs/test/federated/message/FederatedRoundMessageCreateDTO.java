package de.unihamburg.daibetes.api.runs.test.federated.message;

import lombok.Data;

/** Live round-message notification sent by the python wrapper. */
@Data
public class FederatedRoundMessageCreateDTO {
    private Long federatedRunId;
    private String direction;
    private Integer round;
    private String fromParticipant;
    private String toParticipant;
    private String communicationId;
    private String payloadPreview;
}
