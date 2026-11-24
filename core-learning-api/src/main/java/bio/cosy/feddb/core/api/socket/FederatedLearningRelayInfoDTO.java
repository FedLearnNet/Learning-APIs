package bio.cosy.feddb.core.api.socket;

import bio.cosy.feddb.core.execution.run.dto.FederatedRunConfigDTO;
import bio.cosy.feddb.core.services.controller.RelayServerAppVersions;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.util.ArrayList;
import java.util.Set;

@Data
public class FederatedLearningRelayInfoDTO {
    private String id;
    private String key;
    private String channel;
    private String relayKey;
    private Boolean coordinator;

    private String coordinatorId;
    private Integer maxNumClients;
    private Set<String> orderClientIds;
    private RelayServerAppVersions appVersion;

    // Flower-only: set instead of every other field above when the step's app uses Flower - there
    // is no FL-Net relay to join, just the platform's own SuperLink to reach. Doubles as the
    // discriminator {@link #getRunConfig()}'s caller needs to tell a Flower slot from an FL-Net one
    // (see LearningSetupBO#customizeStartup), since channel/id/key are all null for a Flower slot.
    private String superlinkAddress;

    // Flower-only: the platform's cached SuperLink CA certificate (PEM text), if one has been
    // generated and cached yet - see FederatedTrainingSetupBO#flowerClinicSlots. Null on the
    // platform's very first Flower run ever (nothing cached yet, clinic falls back to
    // --insecure, same as before this field existed); populated on every run after that.
    private String caCertificate;

    /**
     * Null-safe accessor. The flag is a nullable Boolean (the mapper may leave it unset), so callers
     * must not unbox it directly. Overrides the Lombok getter and returns a primitive boolean,
     * treating "unset" as not-the-coordinator. Note: serialized output is now true/false, never null.
     */
    public boolean getCoordinator() {
        return Boolean.TRUE.equals(coordinator);
    }

    /** What the app of a clinic needs to join the relay run. */
    @JsonIgnore
    public FederatedRunConfigDTO getRunConfig() {
        FederatedRunConfigDTO config = new FederatedRunConfigDTO();
        config.setChannel(channel);
        config.setClientId(id);
        config.setClientKey(key);
        config.setRelayKey(relayKey);
        config.setCoordinatorId(coordinatorId);
        config.setMaxNumClients(maxNumClients);
        config.setOrderClientIds(orderClientIds != null ? new ArrayList<>(orderClientIds) : null);
        config.setAppVersion(appVersion != null ? appVersion.name() : null);
        config.setSuperlinkAddress(superlinkAddress);
        config.setCaCertificate(caCertificate);
        return config;
    }
}
