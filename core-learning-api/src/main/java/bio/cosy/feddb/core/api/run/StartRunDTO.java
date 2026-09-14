package bio.cosy.feddb.core.api.run;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.List;

@Data
public class StartRunDTO {
    private Long id;

    private RunStatusTypes status;

    private LinkedHashMap<String, Object> hyperParams;
    private LinkedHashMap<String, Object> inputData;
    private LinkedHashMap<String, String> inputFilePaths;

    private boolean supportFederatedLearning;
    private boolean isTrainable;

    private Long federatedAppId;
    private Long federatedAppVersionId;

    // Federated relay/participant config for a START_FEDERATED_RUN. Populated from the step's relay
    // info; the app requires a non-empty participants list (otherwise: "No participants configured").
    private List<FederatedRunParticipantDTO> participants;
    private FederatedRunConfigDTO config;
    private Boolean startAggregator;
    private Integer totalRounds;
}
