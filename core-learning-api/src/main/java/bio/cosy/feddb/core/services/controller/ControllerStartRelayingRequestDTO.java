package bio.cosy.feddb.core.services.controller;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request for the controller's /start-relaying: the PEM encoded certificate the relay server signed for the CSR
 * returned by /start-learning. The run is identified by channel and app key.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ControllerStartRelayingRequestDTO {
    private String channel;
    private String appKey;
    private String certificate;
}
