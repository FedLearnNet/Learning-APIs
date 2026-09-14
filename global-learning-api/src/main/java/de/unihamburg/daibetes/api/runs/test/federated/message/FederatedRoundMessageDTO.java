package de.unihamburg.daibetes.api.runs.test.federated.message;

import bio.cosy.feddb.core.base.BaseDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class FederatedRoundMessageDTO extends BaseDTO {

    private Long federatedRunId;

    private String direction;
    private Integer round;
    private String fromParticipant;
    private String toParticipant;
    private String communicationId;
    private String payloadPreview;
}
