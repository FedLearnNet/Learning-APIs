package bio.cosy.feddb.core.services.controller;

import bio.cosy.feddb.core.api.socket.FederatedLearningRelayInfoDTO;
import bio.cosy.feddb.core.execution.run.dto.FederatedRunConfigDTO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateFLLearningRelayServerResponseDTO {

    private String coordinatorId;

    private String coordinatorKey;
    private Set<String> clientIds;
    private Map<String, String> clientId2ClientKey;

    private String channel;

    /**
     * RelayKey is the relay server's own API key for this run.
     * Clients use it to verify the identity of the relay server
     * during the TCP connection handshake.
     */
    private String relayKey;

    public FederatedRunConfigDTO runConfigOf(RelayServerAppVersions version) {
        FederatedRunConfigDTO config = new FederatedRunConfigDTO();
        config.setChannel(channel);
        config.setRelayKey(relayKey);
        config.setCoordinatorId(coordinatorId);
        config.setMaxNumClients(clientIds.size());
        config.setOrderClientIds(List.copyOf(clientIds));
        config.setAppVersion(version.name());
        return config;
    }

    public FederatedLearningRelayInfoDTO relayInfoFor(RelayServerAppVersions version, String id, String key) {
        FederatedLearningRelayInfoDTO info = new FederatedLearningRelayInfoDTO();
        info.setId(id);
        info.setKey(key);
        info.setChannel(channel);
        info.setRelayKey(relayKey);
        info.setCoordinator(false);
        info.setCoordinatorId(coordinatorId);
        info.setMaxNumClients(clientIds.size());
        info.setOrderClientIds(clientIds);
        info.setAppVersion(version);
        return info;
    }
}
