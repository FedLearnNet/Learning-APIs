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
        return config;
    }
}
