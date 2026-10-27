package de.unihamburg.daibetes.api.development.run.federated;

import bio.cosy.feddb.core.execution.run.types.FederatedParticipantType;
import lombok.Data;

import java.util.LinkedHashMap;

@Data
public class FederatedParticipantCreateDTO {
    private String participantId;
    private FederatedParticipantType role;

    private LinkedHashMap<String, Object> hyperParams;
    private LinkedHashMap<String, String> inputFilePaths;
}
