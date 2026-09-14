package bio.cosy.feddb.core.api.socket;

import bio.cosy.feddb.core.services.controller.RelayServerAppVersions;
import lombok.Data;

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
}
