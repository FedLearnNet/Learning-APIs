package de.unihamburg.daibetes.api.runs.test.federated.participant;

import bio.cosy.feddb.core.api.run.FederatedParticipantType;
import lombok.Data;

import java.util.LinkedHashMap;

@Data
public class FederatedParticipantCreateDTO {
    private String participantId;
    private FederatedParticipantType role;

    private LinkedHashMap<String, Object> hyperParams;
    private LinkedHashMap<String, String> inputFilePaths;
    private FederatedParticipantConfigDTO config;
}
