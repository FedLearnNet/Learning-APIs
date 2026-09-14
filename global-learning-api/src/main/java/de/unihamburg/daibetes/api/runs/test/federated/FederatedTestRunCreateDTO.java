package de.unihamburg.daibetes.api.runs.test.federated;

import de.unihamburg.daibetes.api.runs.test.federated.participant.FederatedParticipantCreateDTO;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class FederatedTestRunCreateDTO {

    private Long federatedAppVersionId;
    private Integer totalRounds;
    private Boolean startAggregator;
    private FederatedTestRunConfigDTO config;
    private List<FederatedParticipantCreateDTO> participants = new ArrayList<>();
}
