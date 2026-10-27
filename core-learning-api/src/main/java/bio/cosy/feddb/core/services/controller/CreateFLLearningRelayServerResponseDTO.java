package bio.cosy.feddb.core.services.controller;

import bio.cosy.feddb.core.api.socket.FederatedLearningRelayInfoDTO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

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
