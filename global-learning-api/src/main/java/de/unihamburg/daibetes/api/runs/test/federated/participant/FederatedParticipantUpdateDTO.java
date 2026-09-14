package de.unihamburg.daibetes.api.runs.test.federated.participant;

import bio.cosy.feddb.core.api.run.RunStatusTypes;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** Live update sent by the python wrapper per participant. */
@Data
public class FederatedParticipantUpdateDTO {
    private Long federatedRunId;
    private String participantId;
    private RunStatusTypes status;
    private Integer currentRound;
    private Integer messagesReceived;
    private Integer messagesSent;
    private List<String> waitingFor = new ArrayList<>();
}
