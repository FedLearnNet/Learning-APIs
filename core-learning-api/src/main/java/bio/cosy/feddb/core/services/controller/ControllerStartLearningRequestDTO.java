package bio.cosy.feddb.core.services.controller;

import bio.cosy.feddb.core.execution.run.dto.FederatedRunConfigDTO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ControllerStartLearningRequestDTO {

    private String channel;
    private String clientId;
    private String clientKey;
    private String relayKey;
    private String runId;
    private String coordinatorId;
    private Integer maxNumClients;
    private List<String> orderClientIds;
    private String appKey = UUID.randomUUID().toString();
    private String appVersion;

    // The app key of a controller run is the relay client id.
    public ControllerStartLearningRequestDTO(FederatedRunConfigDTO relay, Long attemptId) {
        this(relay.getChannel(), relay.getClientId(), relay.getClientKey(), relay.getRelayKey(), String.valueOf(attemptId),
                relay.getCoordinatorId(), relay.getMaxNumClients(), relay.getOrderClientIds(), relay.getClientId(), relay.getAppVersion());
    }
}
