package bio.cosy.feddb.core.execution.run.dto;

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
    // Polling of the controller, as a federated test run in the developer's app engine sets it.
    private Double pollInterval;
    private Double timeout;
    private Integer maxPolls;
    private Boolean useContainerizedController;

    // Flower-only (CommunicationEngine.FLOWER): the platform's own externally-reachable SuperLink
    // address, host:port - what a clinic's SuperNode connects to instead of joining the FL-Net
    // relay. The platform currently runs its SuperLink in --insecure mode (see
    // Flower_Integration_Plan.md's flagged TLS gap), so there is no CA cert to carry yet; the app
    // (pyfedappwrap)'s FederatedRunConfigDTO already has superlinkRootCertificatesPath/
    // supernodeAuthPrivateKeyPath fields ready for when that provisioning exists.
    private String superlinkAddress;
}
