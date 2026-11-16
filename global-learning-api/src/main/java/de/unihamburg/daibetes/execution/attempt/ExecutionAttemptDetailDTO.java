package de.unihamburg.daibetes.execution.attempt;

import bio.cosy.feddb.core.execution.attempt.BaseDetailResultDTO;
import bio.cosy.feddb.core.execution.message.ExecutionMessageRoundDTO;
import de.unihamburg.daibetes.execution.file.ExecutionFileDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
public class ExecutionAttemptDetailDTO extends BaseDetailResultDTO<ExecutionFileDTO> {
    private List<ExecutionMessageRoundDTO> rounds = new ArrayList<>();
    // For a federated step: the participant whose share it is, and how far it got.
    private String participantId;
    private Integer currentRound;
    private Integer messagesSent;
    private Integer messagesReceived;
    private List<String> waitingFor = new ArrayList<>();
}
