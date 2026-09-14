package bio.cosy.feddb.core.services.controller;

import lombok.Data;

import java.util.Map;
import java.util.Set;

@Data
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
}
