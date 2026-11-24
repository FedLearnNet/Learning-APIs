package bio.cosy.feddb.core.execution.run.dto;

import lombok.Data;
import lombok.ToString;

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
    // relay. Also sent to the platform's OWN aggregator attempt (same field, reused as "my own
    // address" rather than "the address to reach" - see FederatedTrainingFlowerAggregatorBO),
    // since the SuperLink's TLS leaf cert needs it as a SAN entry.
    private String superlinkAddress;

    // Flower-only: the platform's cached SuperLink CA certificate content (PEM text), if one has
    // been generated and cached yet (see FederatedTrainingSetupBO#flowerClinicSlots) - what a
    // clinic's SuperNode uses as --root-certificates. Null on the platform's very first Flower
    // run ever; the app (pyfedappwrap) falls back to --insecure in that case, same as before this
    // field existed. The app's own FederatedRunConfigDTO also keeps
    // superlinkRootCertificatesPath/supernodeAuthPrivateKeyPath fields for a locally-provided
    // path (SuperNode auth registration, separately scoped) rather than wire content.
    private String caCertificate;

    // Flower-only, the platform's OWN aggregator attempt only - never a clinic: the private key
    // of caCertificate above, so the aggregator signs each run's SuperLink leaf cert with the same
    // CA clinics were already given (see FederatedTrainingFlowerAggregatorBO#customizeStartup).
    @ToString.Exclude
    private String caPrivateKey;
}
