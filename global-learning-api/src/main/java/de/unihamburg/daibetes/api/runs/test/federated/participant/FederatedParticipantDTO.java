package de.unihamburg.daibetes.api.runs.test.federated.participant;

import bio.cosy.feddb.core.api.run.FederatedParticipantType;
import bio.cosy.feddb.core.api.run.RunStatusTypes;
import bio.cosy.feddb.core.base.BaseDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
public class FederatedParticipantDTO extends BaseDTO {

    private Long federatedRunId;

    private String participantId;
    private FederatedParticipantType role;
    private RunStatusTypes status;
    private Integer currentRound;
    private Integer messagesReceived;
    private Integer messagesSent;
    private List<String> waitingFor = new ArrayList<>();
    private LinkedHashMap<String, Object> hyperParams;
    private LinkedHashMap<String, String> inputFilePaths;
    private FederatedParticipantConfigDTO config;
}
