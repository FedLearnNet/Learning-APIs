package de.unihamburg.daibetes.api.runs.test.federated;

import bio.cosy.feddb.core.api.run.RunStatusTypes;
import bio.cosy.feddb.core.base.BaseDTO;
import de.unihamburg.daibetes.api.runs.test.federated.message.FederatedRoundMessageDTO;
import de.unihamburg.daibetes.api.runs.test.federated.participant.FederatedParticipantDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
public class FederatedTestRunDTO extends BaseDTO {

    private RunStatusTypes status;
    private String error;

    private Integer currentRound;
    private Integer totalRounds;
    private Boolean startAggregator;

    private FederatedTestRunConfigDTO config;
    private LinkedHashMap<String, Object> outputData;

    private String aggregatorId;

    private Long federatedAppId;
    private Long federatedAppVersionId;
    private String federatedAppVersionName;

    private List<FederatedParticipantDTO> participants = new ArrayList<>();
    private List<FederatedRoundMessageDTO> roundMessages = new ArrayList<>();
}
