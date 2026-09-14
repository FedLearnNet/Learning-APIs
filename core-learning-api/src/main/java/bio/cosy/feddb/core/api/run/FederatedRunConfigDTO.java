package bio.cosy.feddb.core.api.run;

import lombok.Data;

import java.util.List;

/**
 * Relay/controller configuration for a federated run, sent inside {@link StartRunDTO} and consumed by
 * the app (pyfedappwrap) {@code FederatedRunConfigDTO} (which allows extra fields). Carries the channel
 * and the relay credentials the app/controller needs to join the round. The app's controller URL
 * is configured separately through its FL_RUN__CONTROLLER_COMM_URL environment variable.
 */
@Data
public class FederatedRunConfigDTO {
    private String channel;

    // Relay credentials (mirrors the proven old-FC ControllerStartLearningRequestDTO field set).
    private String clientId;
    private String clientKey;
    private String relayKey;
    private String coordinatorId;
    private Integer maxNumClients;
    private List<String> orderClientIds;
    private String appVersion;
}
