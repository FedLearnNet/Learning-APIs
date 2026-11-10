package de.unihamburg.daibetes.api.development.run.federated;

import bio.cosy.feddb.core.execution.run.dto.FederatedRunConfigDTO;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class FederatedTestRunCreateDTO {

    private Long federatedAppVersionId;
    private Integer totalRounds;
    private Boolean startAggregator;
    private FederatedRunConfigDTO config;
    // Participants without input files get dummy data of the project's export, as its clients would export it
    private Long projectId;
    private List<FederatedParticipantCreateDTO> participants = new ArrayList<>();
}
